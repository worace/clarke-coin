(ns block-chain.transaction-validations-test
  (:require [clojure.test :refer :all]
            [block-chain.wallet :as wallet]
            [clojure.pprint :refer [pprint]]
            [block-chain.utils :refer :all]
            [block-chain.db :refer [empty-db]]
            [block-chain.chain :as c]
            [block-chain.queries :as q]
            [clojure.math.numeric-tower :as math]
            [block-chain.miner :as miner]
            [block-chain.blocks :as blocks]
            [block-chain.transaction-validations :refer :all]))

(def db (atom empty-db))

(def key-a (wallet/generate-keypair 512))
(def key-b (wallet/generate-keypair 512))

(def a-coinbase (miner/coinbase (:address key-a)))
(def b-coinbase (miner/coinbase (:address key-b)))

(def a-paid (blocks/generate-block [a-coinbase]))
(miner/mine-and-commit-db db a-paid)
(def b-paid (blocks/generate-block [b-coinbase] {:blocks (q/longest-chain @db)}))
;; A and B both start with 25
;; A: 25 B: 25
(miner/mine-and-commit-db db b-paid)
(def b-pays-a-5 (miner/generate-payment key-b
                                        (:address key-a)
                                        5
                                        (q/longest-chain @db)))

;; B pays A 5
;; A: 30, B: 20
(swap! db q/add-block (miner/mine (blocks/generate-block [b-pays-a-5] {:blocks (q/longest-chain @db)})))
(assert (= 3 (q/chain-length @db)))
;; Pending payment transactions:
(def a-pays-b-15 (miner/generate-payment key-a
                                         (:address key-b)
                                         15
                                         (q/longest-chain @db)))

(def a-pays-b-50 (assoc-in a-pays-b-15 [:outputs 0 :amount] 50))


(deftest test-valid-structure
  (is (txn-structure-valid? a-coinbase @db #{}))
  (is (txn-structure-valid? a-pays-b-15 @db #{}))
  (is (not (txn-structure-valid? {} [] #{}))))

(deftest test-sufficient-balance
  (is (sufficient-inputs? a-pays-b-15 @db #{}))
  (is (not (sufficient-inputs? a-pays-b-50 @db #{}))))

(deftest test-all-inputs-have-sources
  (is (inputs-properly-sourced? a-pays-b-15 @db #{}))
  (let [bogus-sourced (assoc-in a-pays-b-15
                                [:inputs 0 :source-hash]
                                "pizza")]
    (is (not (inputs-properly-sourced? bogus-sourced @db #{})))))

(deftest test-valid-signatures
  (is (signatures-valid? a-pays-b-15 @db #{}))
  (let [bogus-sig (assoc-in a-pays-b-15
                            [:outputs 0 :amount]
                            16)]
    (is (not (signatures-valid? bogus-sig @db #{})))))

(deftest test-inputs-unspent
  (is (inputs-unspent? a-pays-b-15 @db #{}))
  (is (= 3 (q/chain-length @db)))
  (is (= 3 (count (mapcat :transactions (q/longest-chain @db)))))
  (is (contains? (->> (q/longest-chain @db)
                      (mapcat :transactions)
                      (into #{}))
                 b-pays-a-5))
  ;; ^ b-pays-a-5 is already in the chain
  ;; so its transaction inputs should show as having already
  ;; been spent
  (is (not (inputs-unspent? b-pays-a-5 @db #{}))))

(deftest test-correct-hash
  (is (valid-hash? a-pays-b-15 @db #{}))
  (is (not (valid-hash? (assoc a-pays-b-15 :hash "pizza") @db #{}))))
