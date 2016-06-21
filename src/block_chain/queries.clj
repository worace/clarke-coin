(ns block-chain.queries
  (:require [clojure.set :refer [intersection union]]
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

(defn common-ancestor [db left-hash right-hash]
  (assert (= java.lang.String (type left-hash)))
  (assert (= java.lang.String (type right-hash)))
  (loop [left (get-block db left-hash)
         right (get-block db right-hash)
         left-search #{(bhash left)}
         right-search #{(bhash right)}
         i 0]
    (cond
      (first (intersection left-search right-search)) (first (intersection left-search right-search))
      (and (nil? left) (nil? right)) "DUNNO"
      (> i 50) "Didnt find ancestor after many loops"
      :else (recur (get-parent db left)
                   (get-parent db right)
                   (if (nil? left)
                     left-search
                     (conj left-search (phash left)))
                   (if (nil? right)
                     right-search
                     (conj right-search (phash right)))
                   (inc i)))))

(defn path-to [db to from]
  (loop [path []
         current (get-block db from)]
    (cond
      (= to (bhash current)) path
      (or (nil? to) (nil? current)) []
      :else (recur (conj path (bhash current))
                   (get-parent db current)))))

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

(defn changeset-highest-hash [db block]
  (if (> (inc (chain-length db (phash block))) (chain-length db (highest-hash db)))
    {:put #{[db-key/highest-hash (bhash block)]}}
    {}))

(defn changeset-transaction-inserts [db block]
  {:put (into #{} (map (fn [txn] [(db-key/txn (:hash txn)) txn]) (:transactions block)))})

(defn changeset-add-utxo [txn-id index output]
  [(db-key/utxo (:address output) txn-id index) output])

(defn changeset-txn-add-utxos [txn]
  (map-indexed (partial changeset-add-utxo (:hash txn)) (:outputs txn)))

(defn changeset-add-utxos [db block]
  {:put (->> (:transactions block)
             (mapcat changeset-txn-add-utxos)
             (into #{}))})

(defn changeset-remove-spent-outputs [db block]
  {:delete (->> (:transactions block)
                (mapcat :inputs)
                (map (fn [i] (if-let [source (source-output db i)]
                               #{(db-key/utxo (:address source) (:source-hash i) (:source-index i))}
                               #{})))
                (reduce union))})

(defn changeset-block-only-inserts [db {{block-hash :hash parent-hash :parent-hash} :header :as block}]
  (merge-with union
              {:put #{[(db-key/block block-hash) block]
                      [(db-key/child-blocks parent-hash) (conj (children db parent-hash) block-hash)]
                      [(db-key/chain-length block-hash) (inc (chain-length db parent-hash))]}
               :delete #{}}
              (changeset-transaction-inserts db block)))

(defn changeset-revert-utxos [db block]
  ;; use process for adding utxos but just take the keys for deletion
  (->> (changeset-add-utxos db block)
       :put
       (map first)
       (into #{})))

(defn changeset-revert-spending-inputs [db block]
  (->> (:transactions block)
       (mapcat :inputs)
       (map (fn [i] (if-let [source (source-output db i)]
                      #{[(db-key/utxo (:address source) (:source-hash i) (:source-index i))
                         source]}
                      #{})))
       (reduce union)))

(defn linear-advance? [db block] (or (nil? (highest-hash db))
                                     (= (highest-hash db) (phash block))))

(defn fork-non-surpassing? [db block]
  (and (not (= (highest-hash db) (phash block)))
       (<= (inc (chain-length db (phash block)))
          (chain-length db))))

(defn fork-surpassing? [db block]
  (and (not (= (highest-hash db) (phash block)))
       (> (inc (chain-length db (phash block)))
          (chain-length db))))

(defn orphan? [db block] (nil? (get-parent db block)))

(defn apply-changeset [db cs]
  (doseq [[k v] (:put cs)]
    (ldb/put (:block-db db) k v))
  (doseq [k (:delete cs)]
    (ldb/delete (:block-db db) k)))

(defn remove-intermediate-inserts
  "If we get a changeset that both inserts and deletes a UTXO we assume the
   delete wins. This works for our cases since transactions can only spend inputs that
   came before them. This function takes a changeset of :put and :delete operations and removes
   any :put operations that also appear in the deletion set."
  [changeset]
  (update changeset :put
          (fn [put-set]
            (into #{}
                  (filter (fn [[k v]] (not (contains? (:delete changeset) k)))
                          put-set)))))

(defn block-txn-revert [db block]
  ;; Bring back any txn sources that had been marked spent by this txn
  {:put (changeset-revert-spending-inputs db block)
   :delete (changeset-revert-utxos db block)})

(defn block-path-txn-revert-changeset [db block-hashes]
  (->> block-hashes
       (map (partial get-block db))
       (map (partial block-txn-revert db))
       (reduce (partial merge-with union))
       (remove-intermediate-inserts)))

(defn block-txn-insert [db block]
  ;; now we are going forward, so apply new utxos
  ;; and delete spent sources
  (merge (changeset-add-utxos db block)
         (changeset-remove-spent-outputs db block)))

(defn block-path-txn-insert-changeset [db block-hashes]
  (->> block-hashes
       (map (partial get-block db))
       (map (partial block-txn-insert db))
       (reduce (partial merge-with union))
       (remove-intermediate-inserts)))

(defn fork-surpassing-utxo-changeset
  "Resolve a blockchain fork with regard to updating the UTXO pool so that balances
   and outputs/inputs appear correct from the perspective of the newly-chosen fork.
   To do this we find a common ancestor between the previous trunk and the new trunk.
   Then we build a changeset to roll-back all of the transactions on the previous trunk
   and apply all of the transactions on the new trunk."
  [db block]
  (let [ca (common-ancestor db (bhash block) (highest-hash db))
        removal-path (path-to db ca (highest-hash db))
        rebuild-path (reverse (path-to db ca (bhash block)))]
    (merge-with union
                (block-path-txn-revert-changeset db removal-path)
                (block-path-txn-insert-changeset db rebuild-path))))

(defn block-insert-scenario [db block]
  (cond
    (linear-advance? db block) [changeset-highest-hash
                                changeset-transaction-inserts
                                changeset-add-utxos
                                changeset-remove-spent-outputs]
    (fork-non-surpassing? db block) [] ;;[fork-surpassing-changeset]
    (fork-surpassing? db block) [fork-surpassing-utxo-changeset
                                 changeset-highest-hash]
    (orphan? db block) []
    :else (throw (Exception. "Unknown block insert case"))))

(defn merge-changesets [db block changesets]
    (->> changesets
         (map (fn [u] (u db block)))
         (reduce (partial merge-with union))))

(defn add-block-no-utxos [db block]
  (apply-changeset db (changeset-block-only-inserts db block)))

(defn add-block [db {{hash :hash parent-hash :parent-hash} :header :as block}]
  (add-block-no-utxos db block)
  (let [addl-changes (block-insert-scenario db block)]
    (apply-changeset db (merge-changesets db block addl-changes)))
  (clear-txn-pool db block))

(defn add-block! [db-ref block]
  (swap! db-ref add-block block))

(defn all-txns
  "Linear iteration through all Transactions in the DB using a leveldb
   iterator around possible transaction key values. Probably a bad idea in
   most circumstances but useful for testing."
  [db]
  (map last (ldb/iterator (:block-db db)
                          (apply str "transaction:" (take 40 (repeat "0")))
                          (apply str "transaction:" (take 40 (repeat "z"))))))

(defn add-peer [db peer]
  (try
    (update-in db [:peers] conj (s/validate Peer peer))
    (catch RuntimeException e
      (do (log/info "Error validating peer:" e)
          db))))

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
