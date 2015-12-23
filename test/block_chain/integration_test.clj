(ns block-chain.integration-test
  (:require [clojure.test :refer :all]
            [clojure.tools.namespace.repl :refer [refresh]]
            [clojure.math.numeric-tower :as math]
            [block-chain.chain :as bc]
            [block-chain.utils :refer :all]
            [block-chain.wallet :as wallet]
            [block-chain.pem :as pem]
            [block-chain.blocks :as blocks]
            [block-chain.miner :as miner]))


(def key-a (wallet/generate-keypair 512))
(def pem-a (pem/public-key->pem-string (.getPublic key-a)))
(def key-b (wallet/generate-keypair 512))
(def pem-b (pem/public-key->pem-string (.getPublic key-b)))

(def easy-difficulty-target (hex-string (math/expt 2 243)))

(deftest generating-coinbase
  (let [cb (miner/coinbase pem-a)]
    (is (= [:inputs :outputs :timestamp :hash] (keys cb)))
    (is (= (:hash cb)
           (get-in cb [:outputs 0 :coords :transaction-id])))))

(deftest test-generating-block
  (let [cb (miner/coinbase pem-a)
        block (blocks/generate-block
               [cb]
               {:target easy-difficulty-target
                :chain []})]
    (is (= [:header :transactions] (keys block)))
    (is (= [:parent-hash :transactions-hash
            :target :timestamp :nonce] (keys (:header block))))
    (is (nil? (:hash block)))
    (is (= (hex-string 0) (get-in block [:header :parent-hash])))
    ))

(deftest test-hashing-block
  (let [block (blocks/hashed
               (blocks/generate-block
                [(miner/coinbase pem-a)]
                {:target easy-difficulty-target}))]
    (is (get-in block [:header :hash]))
    (is (= 0 (get-in block [:header :nonce])))
    (is (not (blocks/meets-target? block)))))

(deftest test-mining-block
  (let [block (miner/mine
               (blocks/hashed
                (blocks/generate-block
                 [(miner/coinbase pem-a)]
                 {:target easy-difficulty-target})))]
    (is (blocks/meets-target? block))
    (is (> (get-in block [:header :nonce]) 0))))

(deftest test-committing-block-to-chain
  (let [b (blocks/generate-block
           [(miner/coinbase pem-a)]
           {:target easy-difficulty-target})
        chain (atom [])]
    (miner/mine-and-commit chain b)
    (is (= 1 (count @chain)))))


(deftest test-mining-multiple-blocks
  )
