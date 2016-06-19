(ns block-chain.queries
  (:require [clojure.set :refer [intersection]]
            [block-chain.schemas :refer :all]
            [block-chain.utils :refer [sha256]]
            [block-chain.db-keys :as db-key]
            [block-chain.log :as log]
            [schema.core :as s]
            [clojure.string :refer [join starts-with?]]
            [clj-leveldb :as ldb]))

(defn bhash [b] (get-in b [:header :hash]))

(defn phash [b] (get-in b [:header :parent-hash]))

(defn get-block [db hash]
  (if hash
    (ldb/get (:block-db db) (db-key/block hash))
    nil))

(defn get-parent [db block] (get-block db (phash block)))

(defn get-txn [db hash] (ldb/get (:block-db db) (db-key/txn hash)))

(defn source-output [db {txn-hash :source-hash index :source-index :as txn-input}]
  (-> (get-txn db txn-hash)
      (get-in [:outputs index])))

(defn chain [db block]
  (if block
    (lazy-seq (cons block
                    (chain db (get-parent db block))))
    (list)))

(defn highest-hash [db]
  (if-let [h (ldb/get (:block-db db) db-key/highest-hash)]
    h
    nil))

(defn highest-block [db]
  (get-block db (highest-hash db)))

(defn longest-chain [db]
  (chain db (highest-block db)))

(defn chain-length
  ([db] (chain-length db (highest-hash db)))
  ([db hash] (or (ldb/get (:block-db db) (db-key/chain-length hash)) 0)))

(defn new-block? [db block]
  (nil? (get-block db (bhash block))))

(defn blocks-since [db hash]
  (->> db
       longest-chain
       (take-while (fn [b] (not (= hash (bhash b)))))
       (reverse)))

(defn children [db hash]
  (set (ldb/get (:block-db db)
                (db-key/child-blocks hash))))

(defn add-transaction-output [db utxo txn-id index]
  (ldb/put (:block-db db) (db-key/utxo (:address utxo) txn-id index) utxo))

(defn utxo-balance [db address]
  (let [key-start (db-key/utxos-range-start address)]
    (if-let [iter (ldb/iterator (:block-db db) key-start)]
      (with-open [iter iter]
        (->> iter
             (take-while (fn [[k v]] (starts-with? k key-start)))
             (map last)
             (map :amount)
             (reduce +)))
      0)))

(defn remove-consumed-utxo [db {txn-hash :source-hash index :source-index :as input}]
  (when-let [source (source-output db input)]
    (ldb/delete (:block-db db)
                (db-key/utxo (:address source) txn-hash index))))

(defn add-transaction [db {hash :hash :as txn}]
  (ldb/put (:block-db db) (db-key/txn hash) txn)
  (doseq [i (:inputs txn)]
    (remove-consumed-utxo db i))
  (dotimes [index (count (:outputs txn))]
    (add-transaction-output db (get-in txn [:outputs index]) hash index)))

(defn coord-only-inputs [txns]
  (->> txns
       (mapcat :inputs)
       (map #(dissoc % :signature))
       (into #{})))

(defn transaction-pool [db] (:transaction-pool db))

(defn remove-overlapping-txns-from-pool [db txns]
  (update db
          :transaction-pool
          #(clojure.set/difference % (into #{} txns))))

(defn remove-txns-with-overlapping-inputs-from-pool [db txns]
  (let [pool (transaction-pool db)
        newly-spent-inputs (coord-only-inputs txns)]
    (->> pool
         (filter (fn [txn]
                   (empty? (intersection newly-spent-inputs
                                         (coord-only-inputs [txn])))))
         (into #{})
         (assoc db :transaction-pool))))

(defn clear-txn-pool [db block]
  (-> db
      (remove-overlapping-txns-from-pool (:transactions block))
      (remove-txns-with-overlapping-inputs-from-pool (:transactions block))))

;; Idea -- Change Sets
;; Try to represent collection of db updates as a value
;; whenever we need to write to db,
;; first: use one function to generate a changeset for that update
;; second: use ldb to write the changeset as a batch
;;
;; Might look like:
(comment
  {:puts [{:key "value" :key2 "val2"}]
   :deletes ["k1" "k2" "k3"]}
;; Or:
  {:puts [["k1" "v1"]
          ["k2" "v2"]
          ["k3" "v3"]
          ]
   :deletes ["k1" "k2" "k3"]}
;; Or:
  {:puts ["k1" "v1" "k2" "v2" "k3" "v3"]
   :deletes ["k1" "k2" "k3"]})

(defn changeset-add-block [db block]
  {:put [] :delete []})

(defn add-block [db {{hash :hash parent-hash :parent-hash} :header :as block}]
  ;; Block insert cases:
  ;; 1 - child of highest block -- simple advancement
  ;;     - Add block
  ;;     - Add block's TXNs
  ;; 2 - orphan -- parent is unknown
  ;;     - ?? Not sure
  ;; 3 - Fork, not (yet) higher
  ;;     - Add block
  ;; 4 - Fork, surpassing
  ;;     - Add block
  ;;     - Add block's TXNs
  ;;     - Add TXNs for all blocks back to common ancestor
  ;;     - Remove all UTXOs from losing side of fork
  ;; TODO: Batch these all in leveldb
  (ldb/put (:block-db db) (db-key/block hash) block)
  (ldb/put (:block-db db)
           (db-key/child-blocks parent-hash)
           (conj (children db parent-hash) hash))
  (ldb/put (:block-db db)
           (db-key/chain-length hash)
           (inc (chain-length db parent-hash)))
  (when (> (chain-length db hash) (chain-length db (highest-hash db)))
    (ldb/put (:block-db db)
             db-key/highest-hash
             hash))
  (doseq [t (:transactions block)]
    (add-transaction db t))
  (clear-txn-pool db block))

(defn fork-path
  "Take a db context and 2 block hashes, one for the head of the new
   fork and one for the head of the existing trunk. Produce a sequence
   of block hashes representing the path from the main chain to the new
   fork head. Includes the fork head as the last element in the path but
   excludes the common ancestor from the main chain."
  [db fork-hash trunk-hash]
  (loop [fork-block (get-block db fork-hash)
         trunk-block (get-block db trunk-hash)
         path (list fork-hash)
         trunk-hashes #{trunk-hash}]
    (cond
      (contains? trunk-hashes (bhash fork-block)) (drop 1 path)
      (or (nil? fork-block) (nil? trunk-block)) (println "FAILED")
      :else (recur (get-parent db fork-block)
                   (get-parent db trunk-block)
                   (conj path (phash fork-block))
                   (conj trunk-hashes (phash trunk-block)))
      )
    )
  )

(defn all-txns
  "Linear iteration through all Transactions in the DB using a leveldb
   iterator around possible transaction key values. Probably a bad idea in
   most circumstances but useful for testing."
  [db]
  (map last (ldb/iterator (:block-db db)
                          (apply str "transaction:" (take 40 (repeat "0")))
                          (apply str "transaction:" (take 40 (repeat "z"))))))

(defn add-block! [db-ref block]
  (swap! db-ref add-block block))

(defn add-peer [db peer]
  (try
    (update-in db [:peers] conj (s/validate Peer peer))
    (catch RuntimeException e
      (do (log/info "Error validating peer:" e)
          db)))
  )
(defn add-peer! [db-ref peer] (swap! db-ref add-peer peer))
(defn remove-peer [db peer] (clojure.set/difference (:peers db) #{peer}))
(defn remove-peer! [db-ref peer] (swap! db-ref remove-peer peer))
(defn peers [db] (into [] (:peers db)))

(defn add-transaction-to-pool! [db-ref txn] (swap! db-ref update :transaction-pool conj txn))

(defn consumed-sources [db txn]
  (->> (:inputs txn)
       (map (partial source-output db))
       (into #{})))

(defn outputs-spent-by-txn-pool [db]
  (->> (transaction-pool db)
       (map (partial consumed-sources db))
       (reduce clojure.set/union)))

(defn utxos [db]
  ;; Have to start from the oldest block (hence reverse them)
  ;; so that older outputs get evicted by the more recent
  ;; inputs that spend them. Obviously should think about doing this
  ;; more efficiently but it is what we got for now
  (loop [[txn & txns] (mapcat :transactions (reverse (longest-chain db)))
         unspent #{}]
    (if (nil? txn)
      unspent
      (let [new-outputs (into #{} (:outputs txn))
            cs (consumed-sources db txn)]
        (recur txns
               (-> unspent
                   (clojure.set/union new-outputs)
                   (clojure.set/difference cs)))))))

(defn assigned-to-key? [key txo] (= key (:address txo)))

(defn unspent-outputs [key db]
  (->> (utxos db)
       (filter (partial assigned-to-key? key))))

(defn balance [db address]
  (utxo-balance db address))

(defn wallet-addr [db] (get-in db [:default-key :address]))

(defn root-block [db] (last (longest-chain db)))

(defn blocks-time-spread [db]
  (->> db
       longest-chain
       (drop-last)
       (map #(get-in % [:header :timestamp]))
       (partition 2 1)
       (map (partial apply -))
       (map #(/ % 1000.0))))
