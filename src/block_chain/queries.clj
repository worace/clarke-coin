(ns block-chain.queries)

(defn bhash [b] (get-in b [:header :hash]))

(defn phash [b] (get-in b [:header :parent-hash]))

(defn chain [db block]
  (if block
    (lazy-seq (cons block
                    (chain db (get-in db [:blocks (get-in block [:header :parent-hash])]))))
    nil))

(defn highest-block [db]
  (get-in db [:blocks (key (apply max-key val (:chains db)))]))

(defn highest-hash [db]
  (bhash (highest-block db)))

(defn longest-chain [db]
  (chain db (highest-block db)))

(defn chain-length [db hash]
  (or (get-in db [:chains hash]) 0))

(defn add-block [db {{hash :hash parent-hash :parent-hash} :header :as block}]
  (-> db
      (assoc-in [:blocks hash] block)
      (update-in [:children parent-hash] conj hash)
      (assoc-in [:chains hash] (inc (chain-length db parent-hash)))
      ))
