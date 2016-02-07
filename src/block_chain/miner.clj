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
     :outputs [{:amount amount :address address}]
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

(defn add-change [txn change-address sources total]
  (let [change (- (apply + (map :amount sources)) total)]
    (if (> change 0)
      (update-in txn
                 [:outputs]
                 conj
                 {:address change-address :amount change})
      txn)))

(defn generate-payment
   "Generates a transaction to pay the specified amount to the
    specified address using provided key. Sources inputs from the
    unspent outputs available to the provided key. If a transaction
    fee is provided, it will be included in the value of inputs
    that are sourced, but not in the value of outputs that are
    spent. (i.e. the fee is the difference between input value
    and output value). Additionally, will roll any remaining
    value into an additional 'change' output back to the paying
    key."
  ([key address amount chain]
   (generate-payment key address amount chain 0))
  ([key address amount chain fee]
   (let [output-pool (bc/unspent-outputs (:public-pem key) chain)
         sources (select-sources (+ amount fee) output-pool)
         txn (raw-payment-txn amount address sources)]
     (-> txn
         (add-change (:public-pem key) sources (+ amount fee))
         (wallet/sign-txn (:private key))
         (txn/hash-txn)
         (txn/tag-coords)))))

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
     (do (println "*******************")
     (println "found new block:")
     (println b)
     (println "*******************")
     (swap! chain conj b))
     (println "didn't find coin, exiting"))))

(defn run-miner! []
  (reset! mine? true)
  (async/go
    (while @mine?
      (mine-and-commit))))
