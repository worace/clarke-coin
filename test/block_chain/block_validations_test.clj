(ns block-chain.block-validations-test)
(ns block-chain.block-validations-test
  (:require [clojure.test :refer :all]
            [block-chain.wallet :as wallet]
            [clojure.pprint :refer [pprint]]
            [block-chain.utils :refer :all]
            [clojure.math.numeric-tower :as math]
            [block-chain.miner :as miner]
            [block-chain.blocks :as blocks]
            [block-chain.block-validations :refer :all]))

(def easy-difficulty (hex-string (math/expt 2 248)))
(def chain (atom []))

(def key-a (wallet/generate-keypair 512))
(def key-b (wallet/generate-keypair 512))

(def a-coinbase (miner/coinbase (:address key-a)))
(def b-coinbase (miner/coinbase (:address key-b)))

(def a-paid (blocks/generate-block [a-coinbase]
                                  {:target easy-difficulty}))

;; A: 25
(miner/mine-and-commit chain a-paid)

;; A pays B 5
(def a-pays-b-5 (miner/generate-payment key-a
                                        (:address key-b)
                                        5
                                        @chain))

;; Block contains 5 A -> B
;; And 25 coinbase -> A
(def un-mined-block (blocks/generate-block [a-pays-b-5]
                                           {:target easy-difficulty
                                            :blocks @chain}))


(deftest test-valid-block-hash
  (is (valid-hash? (first @chain)))
  (is (not (valid-hash? (assoc-in a-paid [:header :hash] "pizza")))))

(deftest test-proper-parent-hash
  (is (valid-parent-hash? (first @chain) []))
  (is (not (valid-parent-hash? (first @chain) @chain)))
  (is (valid-parent-hash? un-mined-block @chain)))

;; Block:
;; Valid target (within allowed threshold)
;; single coinbase
;; coinbase has proper reward
;; coinbase adds correct txn fees
;; block's txn hash is accurate
;; block's timestamp is within allowed threshold
;; block's hash is lower than target
;; txn interactions -- making sure multiple txns in single block don't spend same inputs?
