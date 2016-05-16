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

(defn block-transactions [db coinbase-addr txn-pool]
  (into [(txn/coinbase coinbase-addr db)]
        txn-pool))

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

(defn generate-unsigned-payment
  ([from-address to-address amount chain] (generate-unsigned-payment from-address to-address amount chain 0))
  ([from-address to-address amount chain fee]
   (let [output-pool (bc/unspent-outputs from-address chain)
         sources (select-sources (+ amount fee) output-pool)
         txn (raw-payment-txn amount to-address sources)]
     (-> txn
         (assoc :min-height (count chain))
         (add-change from-address sources (+ amount fee))
         (txn/hash-txn)
         (txn/tag-coords)))))

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
   (txn/sign-txn (generate-unsigned-payment (:address key)
                                            address
                                            amount
                                            chain
                                            fee)
                 (:private key))))

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
  (let [txn-pool (->> (q/transaction-pool db)
                      (block-transactions db (:address wallet/keypair)))]
    (blocks/generate-block txn-pool
                           {:blocks (reverse (take 10 (q/longest-chain db)))
                            :parent-hash (q/highest-hash db)})))

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
