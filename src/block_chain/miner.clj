(ns block-chain.miner
  (:require [clojure.math.numeric-tower :as math]
            [block-chain.utils :refer :all]
            [block-chain.chain :as bc]
            [block-chain.db :as db]
            [block-chain.queries :as q]
            [block-chain.blocks :as blocks]
            [clojure.core.async :as async]
            [block-chain.transactions :as txn]
            [block-chain.block-validations :as block-v]
            [block-chain.peer-notifications :as peers]
            [block-chain.wallet :as wallet]))

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

(defn next-block [db]
  (let [txn-pool (txn/txns-for-next-block db
                                          (:address wallet/keypair))]
    (blocks/generate-block txn-pool
                           db)))

(defn mine-and-commit-db
  ([db] (mine-and-commit-db db (next-block db)))
  ([db candidate]
   (if-let [b (mine candidate)]
     (q/add-block db b)
     db)))

(defn mine-and-commit-db!
  ([] (mine-and-commit-db! db/db))
  ([db-ref] (mine-and-commit-db! db-ref
                                 (next-block @db-ref)))
  ([db-ref pending] (do (swap! db-ref mine-and-commit-db pending)
                        (peers/block-received! (q/highest-block @db-ref))
                        db-ref)))

(defn run-miner! []
  (reset! mine? true)
  (async/go
    (while @mine?
      (mine-and-commit-db!))))
