(ns block-chain.queries
  (:require [clojure.set]
            [clj-leveldb :as ldb]))

(defn bhash [b] (get-in b [:header :hash]))

(defn phash [b] (get-in b [:header :parent-hash]))

(defn get-block [db hash]
  (if hash
    (ldb/get (:block-db db) (str "block:" hash))
    nil))

(defn get-parent [db block] (get-block db (phash block)))

(defn get-txn [db hash] (get-in db [:transactions hash]))

(defn source-output [db {txn-hash :source-hash index :source-index :as txn-input}]
  (get-in db [:transactions txn-hash :outputs index]))

(defn chain [db block]
  (if block
    (lazy-seq (cons block
                    (chain db (get-parent db block))))
    (list)))

(defn highest-hash [{chains :chains}]
  (if (empty? chains)
    nil
    (key (apply max-key val chains))))

(defn highest-block [db]
  (get-block db (highest-hash db)))

(defn longest-chain [db]
  (chain db (highest-block db)))

(defn chain-length
  ([db] (chain-length db (highest-hash db)))
  ([db hash] (or (get-in db [:chains hash]) 0)))

(defn new-block? [db block]
  (nil? (get-block db (bhash block))))

(defn blocks-since [db hash]
  (->> db
       longest-chain
       (take-while (fn [b] (not (= hash (bhash b)))))
       (reverse)))

(defn children [db hash]
  (ldb/get (:block-db db)
           (str "child-blocks:" hash)))

(defn add-transaction [db {hash :hash :as txn}]
  (assoc-in db [:transactions hash] txn))

(defn clear-txn-pool [db block]
  (update db
          :transaction-pool
          #(clojure.set/difference % (into #{} (:transactions block)))))

(defn add-block [db {{hash :hash parent-hash :parent-hash} :header :as block}]
  (as-> db db
    (clear-txn-pool db block)
    (do (ldb/put (:block-db db)
                 (str "block:" hash)
                 block)
        db)
    (do (ldb/put (:block-db db)
                 (str "child-blocks:" parent-hash)
                 (conj (children db parent-hash) hash))
        db)
    ;; (update-in db [:children parent-hash] conj hash)
    (reduce add-transaction db (:transactions block))
    (assoc-in db [:chains hash] (inc (chain-length db parent-hash)))))

(defn add-block! [db-ref block]
  (swap! db-ref add-block block))

(defn add-peer [db peer] (update-in db [:peers] conj peer))
(defn add-peer! [db-ref peer] (swap! db-ref add-peer peer))
(defn remove-peer [db peer] (clojure.set/difference (:peers db) #{peer}))
(defn remove-peer! [db-ref peer] (swap! db-ref remove-peer peer))
(defn peers [db] (into [] (:peers db)))
(defn transaction-pool [db] (:transaction-pool db))

(defn add-transaction-to-pool! [db-ref txn] (swap! db-ref update :transaction-pool conj txn))

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
            consumed-sources (->> (:inputs txn)
                                  (map (partial source-output db))
                                  (into #{}))]
        (recur txns
               (-> unspent
                   (clojure.set/union new-outputs)
                   (clojure.set/difference consumed-sources)))))))

(defn assigned-to-key? [key txo] (= key (:address txo)))

(defn unspent-outputs [key db]
  (->> (utxos db)
       (filter (partial assigned-to-key? key))))

(defn balance [address db]
  (->> (unspent-outputs address db)
       (map :amount)
       (reduce +)))

(defn wallet-addr [db] (get-in db [:default-key :address]))

(defn root-block [db] (last (longest-chain db)))
