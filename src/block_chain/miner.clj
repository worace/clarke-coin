(ns block-chain.miner
  (:require [clojure.math.numeric-tower :as math]
            [block-chain.utils :refer :all]
            [block-chain.chain :as bc]
            [block-chain.blocks :as blocks]
            [clojure.core.async :as async]
            [block-chain.transactions :as txn]
            [block-chain.wallet :as wallet]))

(defn coinbase []
  (txn/hash-txn
   {:inputs []
    :outputs [{:amount 25 :address wallet/public-pem}]
    :timestamp (current-time-millis)}))

(defn mine
  ([block] (mine block (atom true)))
  ([block switch]
   (let [attempt (blocks/hashed block)]
     (when (= 0 (mod (get-in attempt [:header :nonce]) 1000000)) (println "got to nonce: " (get-in attempt [:header :nonce])))
     (if (blocks/meets-target? attempt)
       attempt
       (if (not @switch)
         (do (println "exiting") nil)
         (recur (update-in block [:header :nonce] inc)
              switch))))))

(defonce mine? (atom true))
(defn stop-miner! [] (reset! mine? false))

(defn mine-block
  ([] (mine-block (blocks/generate-block [(coinbase)])))
  ([pending]
   (println "****** Will Mine Block: ******\n" pending "\n***************************")
   (if-let [b (mine pending mine?)]
     (do (println "****** Successfully Mined Block: ******\n" b "\n***************************")
         (bc/add-block! b))
     (println "didn't find coin, exiting"))))

(defn run-miner! []
  (reset! mine? true)
  (async/go
    (while @mine?
      (mine-block))))

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


;; (def beth-pub
;;   "-----BEGIN PUBLIC KEY-----
;; MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA74d+zjjXvCMF0TTHQAJz
;; m3Lgkca4gK3E3XNb+iCipPT7bPOqvl98waBAWOiip+e+h061rC9foJKuhotWe4Gu
;; a0upgIfB5We1H/eEGaEK2ZrfTdQa87JW6ejVkHP2B/lL2ibTmnT/CvJg2seY1YB0
;; r+rBI3ONuvFVzVBNesASXNLrNE+dH0+zrUufDvo2a5y0mt0f4q8QFZDxX2ettE7I
;; zpNt9ea5kRh/gpIeSeaU4uEUt3is/R2yr1JPzQN7Hx3efDfXJ7b6MnL6wU+/0D1R
;; mE5YtARxnvXBZb3sALmg5fdyOVg/L/s2lizHKRk2ASaWCXu/X2Nw9ISuMhWgGMzs
;; twIDAQAB
;; -----END PUBLIC KEY-----\n")

;; (def pay-beth
;;   (let [source-hash (get-in @bc/block-chain [0 :transactions 0 :hash])
;;         source-index 0]
;;     {:inputs [{:source-hash source-hash
;;                :source-index source-index}]
;;      :outputs [:amount 25 :address beth-pub]
;;      :timestamp (current-time-millis)}))


;; (def signed-beth (wallet/sign-txn pay-beth))
;; (def hashed-beth (txn/hash-txn signed-beth))
;; (def beth-block (generate-block [hashed-beth]))
