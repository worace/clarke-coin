(ns block-chain.miner
  (:require [clojure.math.numeric-tower :as math]
            [pandect.algo.sha256 :refer [sha256]]
            [block-chain.utils :refer :all]
            [block-chain.chain :as bc]
            [clojure.core.async :as async]
            [block-chain.transactions :as txn]
            [block-chain.wallet :as wallet]))

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

(defn latest-block-hash
  "Look up the hash of the latest block in the chain.
   Useful for getting parent hash for new blocks."
  []
  (if-let [parent (last @bc/block-chain)]
    (get-in parent [:header :hash])
    (hex-string 0)))

(defn avg-spacing
  "Finds average time spacing in seconds of a series of times"
  [times]
  (->> times
       (partition 2 1)
       (map reverse)
       (map #(apply - %))
       (avg)
       (float)))

(defn adjusted-target [blocks frequency]
  (let [times (map #(get-in % [:header :timestamp]) blocks)
        latest-target (get-in (last blocks) [:header :target])
        ratio (/ (avg-spacing times) frequency)]
    (println "times: " times)
    (println "latest t: " latest-target)
    (println "avg spacing: " (avg-spacing times))
    (println "ratio: " ratio)
    (if (> ratio 1)
      (hex-string (bigint (* 1.1
                             (hex->int latest-target))))
      (hex-string (bigint (* 0.9
                           (hex->int latest-target)))))
    #_(hex-string (bigint (* ratio
                           (hex->int latest-target))))))

(defn next-target
  "Calculate the appropriate next target based on the time frequency
   of recent blocks. Currently just setting a static (easy) target
   until we have more blocks in place to pull frequency data from."
  []
  (let [recent-blocks (take-last 10 @bc/block-chain)]
    (if (and recent-blocks (> (count recent-blocks) 5))
      (adjusted-target recent-blocks 15)
      (hex-string (math/expt 2 237)))))

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

(defn mine
  ([block] (mine block (atom true)))
  ([block switch]
   (let [attempt (hashed block)]
     (when (= 0 (mod (get-in attempt [:header :nonce]) 1000000))
       (println "got to nonce: " (get-in attempt [:header :nonce])))
     (if (meets-target? attempt)
       attempt
       (if (not @switch)
         (do (println "exiting")
             nil)
         (recur (update-in block [:header :nonce] inc)
              switch))))))

(defn coinbase []
  {:inputs []
   :outputs [:amount 25 :address wallet/public-pem]
   :timestamp (current-time-millis)})

(defn gather-transactions
  "Gather pending transactions from the network and add our own coinbase
   reward. (Currently just injecting the coinbase since we don't have other
   txns available yet)"
  []
  (into []
        (map txn/hash-txn [(coinbase)])))

(defonce mine? (atom true))
(defn stop-miner! [] (reset! mine? false))

(defn mine-block
  ([] (mine-block (generate-block (gather-transactions))))
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


(def beth-pub
  "-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA74d+zjjXvCMF0TTHQAJz
m3Lgkca4gK3E3XNb+iCipPT7bPOqvl98waBAWOiip+e+h061rC9foJKuhotWe4Gu
a0upgIfB5We1H/eEGaEK2ZrfTdQa87JW6ejVkHP2B/lL2ibTmnT/CvJg2seY1YB0
r+rBI3ONuvFVzVBNesASXNLrNE+dH0+zrUufDvo2a5y0mt0f4q8QFZDxX2ettE7I
zpNt9ea5kRh/gpIeSeaU4uEUt3is/R2yr1JPzQN7Hx3efDfXJ7b6MnL6wU+/0D1R
mE5YtARxnvXBZb3sALmg5fdyOVg/L/s2lizHKRk2ASaWCXu/X2Nw9ISuMhWgGMzs
twIDAQAB
-----END PUBLIC KEY-----\n")

(def pay-beth
  (let [source-hash (get-in @bc/block-chain [0 :transactions 0 :hash])
        source-index 0]
    {:inputs [{:source-hash source-hash
               :source-index source-index}]
     :outputs [:amount 25 :address beth-pub]
     :timestamp (current-time-millis)}))


(def signed-beth (wallet/sign-txn pay-beth))
(def hashed-beth (txn/hash-txn signed-beth))
(def beth-block (generate-block [hashed-beth]))
