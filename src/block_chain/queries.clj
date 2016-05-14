(ns block-chain.queries)

(defn bhash [b] (get-in b [:header :hash]))

(defn phash [b] (get-in b [:header :parent-hash]))

(defn chain [db block]
  (if block
    (lazy-seq (cons block
                    (chain db (get-in db [:blocks (get-in block [:header :parent-hash])]))))
    nil))

(defn get-block [db hash] (get-in db [:blocks hash]))

(defn get-parent [db block] (get-block db (phash block)))

(defn highest-hash [db]
  (key (apply max-key val (:chains db))))

(defn highest-block [db]
  (get-block db (highest-hash db)))

(defn longest-chain [db]
  (chain db (highest-block db)))

(defn chain-length
  ([db] (chain-length db (highest-hash db)))
  ([db hash] (or (get-in db [:chains hash]) 0)))

(defn blocks-since [db hash]
  (->> db
       longest-chain
       (take-while (fn [b] (not (= hash (bhash b)))))
       (reverse)))

(defn add-block [db {{hash :hash parent-hash :parent-hash} :header :as block}]
  (-> db
      (assoc-in [:blocks hash] block)
      (update-in [:children parent-hash] conj hash)
      (assoc-in [:chains hash] (inc (chain-length db parent-hash)))))
