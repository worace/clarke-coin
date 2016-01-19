(ns block-chain.blocks
  (:require [block-chain.chain :as chain]
            [block-chain.db :as db]
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
  ([transactions] (generate-block transactions {}))
  ([transactions {:keys [parent-hash target timestamp nonce chain]}]
   (let [blocks (or chain @db/block-chain)]
     {:header {:parent-hash (or parent-hash (chain/latest-block-hash chain))
               :transactions-hash (transactions-hash transactions)
               :target (or target (chain/next-target))
               :timestamp (or timestamp (current-time-seconds))
               :nonce (or nonce 0)}
      :transactions transactions})))

(defn meets-target? [{{target :target hash :hash} :header}]
 (< (hex->int hash) (hex->int target)))

(defn hashed [block]
  (assoc-in block [:header :hash] (block-hash block)))
