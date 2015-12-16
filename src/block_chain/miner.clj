(ns block-chain.miner
  (:require [clojure.math.numeric-tower :as math]
            [pandect.algo.sha256 :refer [sha256]]
            [block-chain.utils :refer :all]
            [block-chain.chain :as bc]
            [clojure.core.async :as async]
            [block-chain.transactions :as txn]
            [block-chain.wallet :as wallet]))

(defn transactions-hash [{:keys [transactions]}]
  (sha256 (apply str (map txn/txn-hash transactions))))

(def block-hashable
  (partial cat-keys [:parent-hash
                     :transactions-hash
                     :timestamp
                     :target
                     :nonce]))

(defn block-hash [{:keys [header]}]
  (sha256 (block-hashable header)))

(defn latest-block-hash
  "Look up the hash of the latest block in the chain.
   Useful for getting parent hash for new blocks."
  []
  (if-let [parent (last @bc/block-chain)]
    (get-in parent [:header :hash])
    (hex-string 0)))

(defn next-target
  "Calculate the appropriate next target based on the time frequency
   of recent blocks. Currently just setting a static (easy) target
   until we have more blocks in place to pull frequency data from."
  []
  (hex-string (math/expt 2 238)))

(defn generate-block
  [transactions]
  {:header {:parent-hash (latest-block-hash)
            :transactions-hash (transactions-hash transactions)
            :target (next-target)
            :timestamp (current-time-seconds)
            :nonce 0}
   :transactions transactions})

(defn meets-target? [{{target :target hash :hash} :header}]
 (< (hex->int hash) (hex->int target)))

(defn hashed [block]
  (assoc-in block [:header :hash] (block-hash block)))

(defn mine [block]
  (let [attempt (hashed block)]
    (if (meets-target? attempt)
      attempt
      (recur (update-in block [:header :nonce] inc)))))

(defn coinbase []
  {:inputs [] :outputs [:amount 25 :address wallet/public-pem]})

(defn gather-transactions
  "Gather pending transactions from the network and add our own coinbase
   reward. (Currently just injecting the coinbase since we don't have other
   txns available yet)"
  []
  [(coinbase)])

(defn find-next-block [] (mine (generate-block (gather-transactions))))

(def mine? (atom true))
(defn stop-miner! [] (reset! mine? false))
(defn run-miner! []
  (reset! mine? true)
  (async/go
    (while @mine?
      (let [b (find-next-block)]
        (println "****** Found a Block ******")
        (println b)
        (println "***************************")
        (bc/add-block! b)))))

;; wallet -- using blockchain to find
;; balance / transaction outputs
;; wallet:
;; -- (available-utxos pub-key)
;; -- (available-balance pub-key)
;; wallet:
;; (pay-to-address pub-key amount)
;; -- find utxo totaling this amount
;; -- generate new transaction transferring to that address


;; transactions-pool
;; -- keeping track of currently available / pending transactions
;; -- pull from these when generating new block

