(ns block-chain.blocks
  (:require [block-chain.queries :as q]
            [block-chain.db :as db]
            [block-chain.target :as target]
            [block-chain.utils :refer :all]))

(defn transactions-hash [transactions]
  (sha256 (apply str (map :hash transactions))))

(def block-hashable
  (partial cat-keys [:parent-hash
                     :transactions-hash
                     :timestamp
                     :target
                     :nonce]))

(defn block-hash [{:keys [header]}]
  (sha256 (block-hashable header)))

(defn generate-block
  ([txns db] (generate-block txns db {}))
  ([transactions db {:keys [parent-hash target timestamp nonce]}]
   {:header {:parent-hash (or parent-hash (q/highest-hash db) (hex-string 0))
             :transactions-hash (transactions-hash transactions)
             :target (or target
                         ;; drop one block to make sure we don't count the genesis block...
                         (target/next-target (drop-last (take 30 (q/longest-chain db)))))
             :timestamp (or timestamp (current-time-millis))
             :nonce (or nonce 0)}
    :transactions transactions}))

(defn meets-target? [{{target :target hash :hash} :header}]
 (< (hex->int hash) (hex->int target)))

(defn hashed [block]
  (assoc-in block [:header :hash] (block-hash block)))
