(ns block-chain.blocks
  (:require [block-chain.chain :as chain]
            [pandect.algo.sha256 :refer [sha256]]
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
  [transactions]
  {:header {:parent-hash (chain/latest-block-hash)
            :transactions-hash (transactions-hash transactions)
            :target (chain/next-target)
            :timestamp (current-time-seconds)
            :nonce 0}
   :transactions transactions})

(defn meets-target? [{{target :target hash :hash} :header}]
 (< (hex->int hash) (hex->int target)))

(defn hashed [block]
  (assoc-in block [:header :hash] (block-hash block)))
