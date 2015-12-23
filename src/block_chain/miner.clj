(ns block-chain.miner
  (:require [clojure.math.numeric-tower :as math]
            [block-chain.utils :refer :all]
            [block-chain.chain :as bc]
            [block-chain.blocks :as blocks]
            [clojure.core.async :as async]
            [block-chain.transactions :as txn]
            [block-chain.wallet :as wallet]))

(defn coinbase
  ([] (coinbase wallet/public-pem))
  ([address] (txn/tag-coords
              (txn/hash-txn
               {:inputs []
                :outputs [{:amount 25 :address address}]
                :timestamp (current-time-millis)}))))

(defn payment
  [address source-hash source-index]
  (txn/hash-txn
   (wallet/sign-txn
    {:inputs [{:source-hash source-hash :source-index source-index}]
     :outputs [{:amount 25 :address address}]
     :timestamp (current-time-millis)})))

(defn mine
  ([block] (mine block (atom true)))
  ([block switch]
   (let [attempt (blocks/hashed block)]
     #_(when (= 0 (mod (get-in attempt [:header :nonce]) 1000000)) (println "got to nonce: " (get-in attempt [:header :nonce])))
     (if (blocks/meets-target? attempt)
       attempt
       (if (not @switch)
         (do (println "exiting") nil)
         (recur (update-in block [:header :nonce] inc)
              switch))))))

(defonce mine? (atom true))
(defn stop-miner! [] (reset! mine? false))

(defn mine-and-commit
  ([] (mine-and-commit bc/block-chain))
  ([chain] (mine-and-commit chain (blocks/generate-block [(coinbase)])))
  ([chain pending]
   (if-let [b (mine pending mine?)]
     (swap! chain conj b)
     (println "didn't find coin, exiting"))))

(defn run-miner! []
  (reset! mine? true)
  (async/go
    (while @mine?
      (mine-and-commit))))

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


;; need to pay 25 from current wallet public pem to:
;;"-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAn04rVGD/selxmPcYRmjc\nHE19e5XQOueBekYEdQHD5q06mzuLQqErjJDANt80JjF6Y69dOU23cqlZ1B/2Pj48\nK+OROFBlrT5usrAJa6we0Ku33w6avl47PXanhcfi39GKNr8RadCKHoG1klvKqVEm\nuhJO/2foXAb6LATB0YoQuH8sDvUmLHSSPTTCBO2YGtsCvlMBNxdnvGVyZA5iIPwu\nw7DN00jG8RJn0KQRDgTM+nFNxcw9bIOrfSxOmNTDo1y8EFwFiYZ6rORLN+cNL50T\nU1Kl/ShX0dfvXauSjliVSl3sll1brVC500HYlAK61ox5BakdZG6R+3tJKe1RAs3P\nNQIDAQAB\n-----END PUBLIC KEY-----\n"

;; block will have 2 transactions:
;; - coinbase
;; - payment

;; current chain has 4 blocks, last of which is
;; ours
;; pay using output 0
;; from txn "e6f4ed3ff30f3936d99385d33f6410c22781359e3cfe69ccabcad109ee9ab40f"

#_(let [recip "-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAn04rVGD/selxmPcYRmjc\nHE19e5XQOueBekYEdQHD5q06mzuLQqErjJDANt80JjF6Y69dOU23cqlZ1B/2Pj48\nK+OROFBlrT5usrAJa6we0Ku33w6avl47PXanhcfi39GKNr8RadCKHoG1klvKqVEm\nuhJO/2foXAb6LATB0YoQuH8sDvUmLHSSPTTCBO2YGtsCvlMBNxdnvGVyZA5iIPwu\nw7DN00jG8RJn0KQRDgTM+nFNxcw9bIOrfSxOmNTDo1y8EFwFiYZ6rORLN+cNL50T\nU1Kl/ShX0dfvXauSjliVSl3sll1brVC500HYlAK61ox5BakdZG6R+3tJKe1RAs3P\nNQIDAQAB\n-----END PUBLIC KEY-----\n"
      source-hash "e6f4ed3ff30f3936d99385d33f6410c22781359e3cfe69ccabcad109ee9ab40f"
      source-index 0
      txns [(coinbase)
            (payment recip source-hash 0)]]
  (mine-block (generate-block txns)))
