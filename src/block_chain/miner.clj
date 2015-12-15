(ns block-chain.miner
  (:require [clojure.math.numeric-tower :as math]
            [pandect.algo.sha256 :refer [sha256]]
            [block-chain.transactions :as txn]
            [block-chain.wallet :as wallet]))

(defn current-time-seconds [] (int (/ (System/currentTimeMillis) 1000.0)))

(defn hex-string [num] (format "%064x"
                               (biginteger num)))

(defn select-values [map ks]
  (reduce (fn [values key] (conj values (get map key)))
          []
          ks))

(defn transactions-hash [{:keys [transactions]}]
  (sha256 (apply str (map txn/txn-hash transactions))))

(defn block-hash [{:keys [header]}]
  (sha256 (apply str (select-values header
                                    [:parent-hash
                                     :transactions-hash
                                     :timestamp
                                     :target
                                     :nonce]))))

(defn latest-block-hash
  "Look up the hash of the latest block in the chain.
   Useful for getting parent hash for new blocks. Eventually
   this will need to grab the latest one off the chain but for now
   we'll just zero it out."
  []
  (hex-string 0))

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

(defn hex->int
  "Read hex string and convert it to big integer."
  [hex-string]
  (bigint (java.math.BigInteger. hex-string 16)))

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
  {:inputs [] :outputs [:amount 10 :address wallet/public-pem]})

(defn gather-transactions
  "Gather pending transactions from the network and add our own coinbase
   reward. (Currently just injecting the coinbase since we don't have other
   txns available yet)"
  []
  [(coinbase)])

(defn find-next-block
  []
  (mine (generate-block (gather-transactions))))

;; saving mined blocks
;; need to store them somewhere -- block-chain ns?
;; block-chain functions
;; -- add block
;; -- find block by hash
;; -- find block by height
;; -- 

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
;; 

