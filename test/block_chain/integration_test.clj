(ns block-chain.integration-test
  (:require [clojure.test :refer :all]
            [clojure.tools.namespace.repl :refer [refresh]]
            [clojure.math.numeric-tower :as math]
            [block-chain.chain :as bc]
            [block-chain.utils :refer :all]
            [block-chain.wallet :as wallet]
            [block-chain.pem :as pem]
            [block-chain.transactions :as txn]
            [block-chain.blocks :as blocks]
            [block-chain.miner :as miner]))


(def key-a (wallet/generate-keypair 512))
(def pem-a (:public-pem key-a))
(def key-b (wallet/generate-keypair 512))
(def pem-b (:public-pem key-b))

(def easy-difficulty-target (hex-string (math/expt 2 248)))

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
    (is (= (hex-string 0) (get-in block [:header :parent-hash])))))

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
  (let [chain (atom [])]
    (miner/mine-and-commit chain
                           (blocks/generate-block
                            [(miner/coinbase pem-a)]
                            {:target easy-difficulty-target :blocks @chain}))
    (is (= 1 (count @chain)))))


(deftest test-mining-multiple-blocks
  (let [chain (atom [])]
    (miner/mine-and-commit chain
                           (blocks/generate-block
                            [(miner/coinbase pem-a)]
                            {:target easy-difficulty-target :blocks @chain}))
    (miner/mine-and-commit chain
                           (blocks/generate-block
                            [(miner/coinbase pem-a)]
                            {:target easy-difficulty-target :blocks @chain}))
    (miner/mine-and-commit chain
                           (blocks/generate-block
                            [(miner/coinbase pem-a)]
                            {:target easy-difficulty-target :blocks @chain}))
    (is (= 3 (count @chain)))
    (is (= (get-in (get @chain 0) [:header :hash])
           (get-in (get @chain 1) [:header :parent-hash])))
    (is (= (get-in (get @chain 1) [:header :hash])
           (get-in (get @chain 2) [:header :parent-hash])))))


(deftest test-checking-balances
  (let [chain (atom [])]
    (miner/mine-and-commit chain
                           (blocks/generate-block
                            [(miner/coinbase pem-a)]
                            {:target easy-difficulty-target :blocks @chain}))
    (miner/mine-and-commit chain
                           (blocks/generate-block
                            [(miner/coinbase pem-a)]
                            {:target easy-difficulty-target :blocks @chain}))
    (miner/mine-and-commit chain
                           (blocks/generate-block
                            [(miner/coinbase pem-a)]
                            {:target easy-difficulty-target :blocks @chain}))
    (is (= 3 (count (bc/unspent-outputs pem-a @chain))))
    (is (= 75 (bc/balance pem-a @chain)))))

#_(deftest test-transferring-outputs
  (let [chain (atom [])]
    (miner/mine-and-commit chain
                           (blocks/generate-block
                            [(miner/coinbase pem-a)]
                            {:target easy-difficulty-target :blocks @chain}))
    (is (= 1 (count (bc/unspent-outputs pem-a @chain))))
    (is (= 25 (bc/balance pem-a @chain)))
    (let [source (get-in (last @chain)
                         [:transactions 0 :outputs 0])
          payment (miner/payment (:private key-a) pem-b source)]
      (miner/mine-and-commit chain
                             (blocks/generate-block
                              [(miner/coinbase pem-a)
                               payment]
                              {:target easy-difficulty-target :blocks @chain}))
      ;; 2 blocks containing 3 transactions
      ;; 2 coinbases for A and 1 txn transferring
      ;; 1st coinbase to B
      (is (= 2 (count @chain)))
      (is (= 1 (count (bc/unspent-outputs pem-a @chain))))
      (is (= 3 (count (bc/transactions @chain) )))
      (is (= 1 (count (bc/unspent-outputs pem-b @chain))))
      (is (= 25 (bc/balance pem-a @chain)))
      (is (= 25 (bc/balance pem-b @chain))))))

(deftest test-selecting-sources-from-output-pool
  (let [pool [{:amount 25 :address 1234}
              {:amount 14 :address 1234}]
        sources (miner/select-sources 25 pool)]
    (is (= (take 1 pool) sources)))
  (let [pool [{:amount 11 :address 1234}
              {:amount 14 :address 1234}]
        sources (miner/select-sources 25 pool)]
    (is (= pool sources))))

(deftest test-generating-payment-fails-without-sufficient-funds
  (let [chain (atom [])]
    (miner/mine-and-commit chain
                           (blocks/generate-block
                            [(miner/coinbase pem-a)]
                            {:target easy-difficulty-target :blocks @chain}))
    (is (= 1 (count (bc/unspent-outputs pem-a @chain))))
    (is (= 25 (bc/balance pem-a @chain)))
    (is (thrown? AssertionError
                 (miner/generate-payment key-a pem-b 26 @chain)))))

(deftest test-generating-raw-payment-txn
  (let [sources (concat (:outputs (miner/coinbase pem-a))
                        (:outputs (miner/coinbase pem-b)))
        raw-p (miner/raw-payment-txn 50 "addr" sources)]
    (is (= 2 (count (:inputs raw-p))))
    (is (= (into #{} (vals (:coords (first sources))))
           (into #{} (vals (first (:inputs raw-p))))))
    (is (= (into #{} (vals (:coords (last sources))))
           (into #{} (vals (last (:inputs raw-p))))))))

(deftest test-generating-payment-produces-valid-txn
  (let [chain (atom [])]
    (miner/mine-and-commit chain
                           (blocks/generate-block
                            [(miner/coinbase pem-a)]
                            {:target easy-difficulty-target :blocks @chain}))
    (let [p (miner/generate-payment key-a pem-b 25 @chain)
          sig (:signature (first (:inputs p)))]
      (is (= 1 (count (:inputs p))))
      (is (= 1 (count (:outputs p))))
      (is (= 25 (reduce + (map :amount (:outputs p)))))
      (is (wallet/verify
           sig
           (txn/txn-signable p)
           (:public key-a))))))

(deftest test-generating-payment-from-multiple-inputs
  (let [chain (atom [])]
    (miner/mine-and-commit chain (blocks/generate-block [(miner/coinbase pem-a)] {:target easy-difficulty-target :blocks @chain}))
    (miner/mine-and-commit chain (blocks/generate-block [(miner/coinbase pem-a)] {:target easy-difficulty-target :blocks @chain}))
    (let [p (miner/generate-payment key-a pem-b 50 @chain)
          sig (:signature (first (:inputs p)))]
      (is (= 2 (count (:inputs p))))
      (is (= 1 (count (:outputs p))))
      (is (= 50 (reduce + (map :amount (:outputs p)))))
      (is (wallet/verify
           sig
           (txn/txn-signable p)
           (:public key-a))))))

(deftest test-generating-payment-with-transaction-fee
  (let [chain (atom [])]
    (miner/mine-and-commit chain (blocks/generate-block [(miner/coinbase pem-a)] {:target easy-difficulty-target :blocks @chain}))
    (let [p (miner/generate-payment key-a pem-b 24 @chain 1)
          sig (:signature (first (:inputs p)))]
      (is (= 1 (count (:inputs p))))
      (is (= 1 (count (:outputs p))))
      (is (= 24 (reduce + (map :amount (:outputs p)))))
      (let [sources (map (fn [i]
                           (bc/source-output i @chain))
                         (:inputs p))]
        (is (= 25 (reduce + (map :amount sources)))))
      (is (wallet/verify
           sig
           (txn/txn-signable p)
           (:public key-a))))))

(deftest test-generating-payment-with-change
  (let [chain (atom [])]
    (miner/mine-and-commit chain (blocks/generate-block [(miner/coinbase pem-a)] {:target easy-difficulty-target :blocks @chain}))
    (let [p (miner/generate-payment key-a pem-b 15 @chain 3)
          sig (:signature (first (:inputs p)))]
      (is (= 1 (count (:inputs p))))
      (is (= 2 (count (:outputs p))))
      (is (= 15 (:amount (first (:outputs p)))))
      (is (= pem-b (:address (first (:outputs p)))))
      (is (= 7 (:amount (last (:outputs p)))))
      (is (= pem-a (:address (last (:outputs p)))))
      (is (= 22 (reduce + (map :amount (:outputs p)))))
      (let [sources (map (fn [i]
                           (bc/source-output i @chain))
                         (:inputs p))]
        (is (= 25 (reduce + (map :amount sources)))))
      (is (wallet/verify
           sig
           (txn/txn-signable p)
           (:public key-a))))))

(deftest test-generating-unsigned-payment
  (let [chain (atom [])]
    (miner/mine-and-commit chain (blocks/generate-block [(miner/coinbase pem-a)] {:target easy-difficulty-target :blocks @chain}))
    (let [p (miner/generate-unsigned-payment pem-a pem-b 15 @chain 3)
          sig (:signature (first (:inputs p)))]
      (is (= 1 (count (:inputs p))))
      (is (= 2 (count (:outputs p))))
      (is (= 15 (:amount (first (:outputs p)))))
      (is (= pem-b (:address (first (:outputs p)))))
      (is (= 7 (:amount (last (:outputs p)))))
      (is (= pem-a (:address (last (:outputs p)))))
      (is (= 22 (reduce + (map :amount (:outputs p)))))
      (let [sources (map (fn [i]
                           (bc/source-output i @chain))
                         (:inputs p))]
        (is (= 25 (reduce + (map :amount sources)))))
      (is (= nil sig))
      ;; verify that we can subsequently sign the txn as needed
      (let [signed (wallet/sign-txn p (:private key-a))]
        (is (wallet/verify (:signature (first (:inputs signed)))
                           (txn/txn-signable signed)
                           (:public key-a)))))))
