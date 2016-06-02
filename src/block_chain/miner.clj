(ns block-chain.miner
  (:import [java.util UUID])
  (:require [clojure.math.numeric-tower :as math]
            [block-chain.utils :refer :all]
            [block-chain.db :as db]
            [block-chain.queries :as q]
            [clojure.core.async :as async]
            [block-chain.blocks :as blocks]
            [block-chain.log :as log]
            [block-chain.transactions :as txn]
            [block-chain.block-validations :as block-v]
            [block-chain.peer-notifications :as peers]))

(defonce miner-interrupts (atom {}))
(defn interrupt-miner! []
  (doseq [i (vals @miner-interrupts)]
    (reset! i true)))

(defn mine [block]
  (let [miner-id (UUID/randomUUID)
        interrupt (atom false)]
    (swap! miner-interrupts assoc miner-id interrupt)
    (try
      (loop [attempt (blocks/hashed block)]
        (when (= 0 (mod (get-in attempt [:header :nonce]) 1000000))
          (log/info "got to nonce: " (get-in attempt [:header :nonce]) "against parent" (q/phash attempt)))
        (cond
          (blocks/meets-target? attempt) attempt
          @interrupt (do (log/info "****** Miner interrupted ******\nAgainst parent:" (q/phash attempt)) nil)
          :else (recur (blocks/hashed (update-in attempt [:header :nonce] inc)))))
      (finally (swap! miner-interrupts dissoc miner-id)))))

(defonce mine? (atom true))
(defn stop-miner! [] (reset! mine? false))

(defn next-block
  ([db] (next-block db (q/wallet-addr db)))
  ([db coinbase-addr]
   (let [txn-pool (txn/txns-for-next-block db coinbase-addr)]
     (blocks/generate-block txn-pool db))))

(defn mine-and-commit-db
  ([db] (mine-and-commit-db db (next-block db)))
  ([db candidate]
   (if-let [b (mine candidate)]
     (do
       (log/info "Miner mined new block" (q/bhash b))
       (q/add-block db b))
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
        (try
          (mine-and-commit-db!)
          (catch Exception e (log/info "\n\n*************\nError in Miner: " (.getMessage e)))))))
