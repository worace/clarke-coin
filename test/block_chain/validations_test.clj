(ns block-chain.validations-test
  (:require [clojure.test :refer :all]
            [block-chain.wallet :as wallet]
            [block-chain.utils :refer :all]
            [clojure.math.numeric-tower :as math]
            [block-chain.miner :as miner]
            [block-chain.blocks :as blocks]
            [block-chain.validations :refer :all]))

(def easy-difficulty (hex-string (math/expt 2 248)))
(def chain (atom []))

(def key-a (wallet/generate-keypair 512))
(def key-b (wallet/generate-keypair 512))

(def a-coinbase (miner/coinbase (:address key-a)))
(def b-coinbase (miner/coinbase (:address key-b)))
(def a-paid (blocks/generate-block [a-coinbase]
                                  {:target easy-difficulty}))
(def b-paid (blocks/generate-block [b-coinbase]
                                  {:target easy-difficulty}))
(miner/mine-and-commit chain a-paid)
(miner/mine-and-commit chain b-paid)

;; A and B both start with 25

(def a-pays-b-15 (miner/generate-payment key-a
                                         (:address key-b)
                                         15
                                         @chain))
(def a-pays-b-50
  (assoc-in a-pays-b-15 [:outputs 0 :amount] 50))


(deftest test-valid-structure
  (is (txn-structure-valid? a-coinbase [] #{}))
  (is (txn-structure-valid? a-pays-b-15 [] #{}))
  (is (not (txn-structure-valid? {} [] #{}))))

(deftest test-sufficient-balance
  (is (sufficient-inputs? a-pays-b-15 @chain #{}))
  (is (not (sufficient-inputs? a-pays-b-50 @chain #{}))))

(deftest test-inputs-unspent)
(deftest test-valid-outputs)

