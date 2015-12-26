(ns block-chain.miner
  (:require [clojure.math.numeric-tower :as math]
            [block-chain.utils :refer :all]
            [block-chain.chain :as bc]
            [block-chain.blocks :as blocks]
            [clojure.core.async :as async]
            [block-chain.transactions :as txn]
            [block-chain.wallet :as wallet]))

(def coinbase-reward 25)
(defn coinbase
  ([] (coinbase (:public-pem wallet/keypair)))
  ([address] (txn/tag-coords
              (txn/hash-txn
               {:inputs []
                :outputs [{:amount coinbase-reward :address address}]
                :timestamp (current-time-millis)}))))

(defn payment
  [paying-key receiving-key source-output]
  (let [tx-hash (get-in source-output [:coords :transaction-id])
        index   (get-in source-output [:coords :index])
        payment {:inputs [{:source-hash tx-hash
                           :source-index index}]
                 :outputs [{:amount 25
                            :address receiving-key}]
                 :timestamp (current-time-millis)}]
    (-> payment
        (wallet/sign-txn paying-key)
        (txn/hash-txn))))

(defn raw-payment-txn
  [amount address sources]
  (let [inputs (into [] (map (fn [s]
                               {:source-hash (get-in s [:coords :transaction-id])
                                :source-index (get-in s [:coords :index])})
                             sources))]
    {:inputs inputs
     :outputs {:amount amount :address address}
     :timestamp (current-time-millis)}))

(defn select-sources
  [amount output-pool]
  (let [avail (reduce + (map :amount output-pool))]
    (assert (>= avail amount) (str "Only found " avail " to fund " amount ".")))
  (let [greaters (filter #(>= (:amount %) amount) output-pool)
        lessers (filter #(< (:amount %) amount) output-pool)]
    (if (first greaters)
      (take 1 greaters)
      (loop [sources []
             pool lessers]
        (if (>= (reduce + (map :amount sources)) amount)
          sources
          (recur (conj sources (first pool))
                 (rest pool)))))))

(defn generate-payment [key address amount chain]
  (let [output-pool (bc/unspent-outputs (:public-pem key) chain)
        sources (select-sources amount output-pool)]
    
    ;; get payment txn
    ;; sign it
    ;; tag it
    ;; hash it
    ))

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
