(ns block-chain.queries)

(defn bhash [b] (get-in b [:header :hash]))

(defn phash [b] (get-in b [:header :parent-hash]))

(defn get-block [db hash] (get-in db [:blocks hash]))

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
  (not (contains? (:blocks db) (bhash block))))

(defn blocks-since [db hash]
  (->> db
       longest-chain
       (take-while (fn [b] (not (= hash (bhash b)))))
       (reverse)))

(defn add-transaction [db {hash :hash :as txn}]
  (assoc-in db [:transactions hash] txn))

(defn clear-txn-pool [db block]
  (update db
          :transaction-pool
          #(clojure.set/difference % (into #{} (:transactions block)))))

(defn add-block [db {{hash :hash parent-hash :parent-hash} :header :as block}]
  (as-> db db
    (clear-txn-pool db block)
    (assoc-in db [:blocks hash] block)
    (update-in db [:children parent-hash] conj hash)
    (reduce add-transaction db (:transactions block))
    (assoc-in db [:chains hash] (inc (chain-length db parent-hash)))))

(defn add-peer [db peer] (update-in db [:peers] conj peer))
(defn add-peer! [db-ref peer] (swap! db-ref add-peer peer))
(defn remove-peer [db peer] (clojure.set/difference (:peers db) #{peer}))
(defn remove-peer! [db-ref peer] (swap! db-ref remove-peer peer))
(defn peers [db] (into [] (:peers db)))
(defn transaction-pool [db] (:transaction-pool db))

(defn add-transaction-to-pool! [db-ref txn] (swap! db-ref update :transaction-pool conj txn))

(defn utxos [db]
  (loop [[txn & txns] (mapcat :transactions (longest-chain db))
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
