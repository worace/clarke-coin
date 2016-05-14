(ns block-chain.blocks
  (:require [block-chain.chain :as chain]
            [block-chain.queries :as q]
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
  ([transactions] (generate-block transactions {:blocks []}))
  ([transactions {:keys [parent-hash target timestamp nonce blocks]}]
   {:header {:parent-hash (or parent-hash
                              (chain/latest-block-hash blocks))
             :transactions-hash (transactions-hash transactions)
             :target (or target (chain/next-target blocks))
             :timestamp (or timestamp (current-time-millis))
             :nonce (or nonce 0)}
    :transactions transactions}))

(defn generate-block-db
  ;; ([transactions] (generate-block transactions {:blocks db/empty-db}))
  ([transactions {:keys [parent-hash target timestamp nonce db]}]
   {:header {:parent-hash (or parent-hash (q/highest-hash db))
             :transactions-hash (transactions-hash transactions)
             :target (or target
                         (target/next-target (take 10 (q/longest-chain db))))
             :timestamp (or timestamp (current-time-millis))
             :nonce (or nonce 0)}
    :transactions transactions}))

(defn meets-target? [{{target :target hash :hash} :header}]
 (< (hex->int hash) (hex->int target)))

(defn hashed [block]
  (assoc-in block [:header :hash] (block-hash block)))
