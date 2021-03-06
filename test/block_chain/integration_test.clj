(ns block-chain.integration-test
  (:require [clojure.test :refer :all]
            [clojure.math.numeric-tower :as math]
            [clojure.pprint :refer [pprint]]
            [block-chain.utils :refer :all]
            [block-chain.wallet :as wallet]
            [block-chain.key-serialization :as ks]
            [block-chain.transactions :as txn]
            [block-chain.blocks :as blocks]
            [block-chain.db :as db]
            [block-chain.queries :as q]
            [block-chain.test-helper :as th]
            [block-chain.miner :as miner]))

(def key-a wallet/keypair)
(def address-a (:address wallet/keypair))
(def key-b (wallet/generate-keypair 512))
(def address-b (:address key-b))

(def db (atom nil))
(defn setup [tests]
  (with-open [conn (th/temp-db-conn)]
    (reset! db (db/db-map conn))
    (tests)))
(use-fixtures :each setup)

(deftest generating-coinbase
  (let [cb (txn/coinbase @db address-a)]
    (is (= #{:inputs :outputs :timestamp :hash :min-height} (into #{} (keys cb))))
    (is (= (:hash cb)
           (get-in cb [:outputs 0 :coords :transaction-id])))))

(deftest test-mining-block
  (let [block (-> (txn/coinbase @db address-a)
                  (blocks/generate-block @db)
                  (miner/mine))]
    (is (blocks/meets-target? block))))

(deftest test-committing-block-to-chain
  (is (= 0 (q/chain-length @db)))
  (miner/mine-and-commit-db! db
                             (blocks/generate-block
                              [(txn/coinbase @db address-a)]
                              @db))
  (is (= 1 (q/chain-length @db))))

(deftest test-mining-multiple-blocks
  (dotimes [_ 4] (miner/mine-and-commit-db! db))
  (is (= 4 (q/chain-length @db)))
  (is (= (q/phash (first (q/longest-chain @db)))
         (q/bhash (second (q/longest-chain @db)))))
  (is (= (q/phash (first (drop 1 (q/longest-chain @db))))
         (q/bhash (second (drop 1 (q/longest-chain @db))))))
  (is (= (q/phash (first (drop 2 (q/longest-chain @db))))
         (q/bhash (second (drop 2 (q/longest-chain @db)))))))

(deftest test-checking-balances
  (dotimes [n 3] (miner/mine-and-commit-db! db))
  (is (= 3 (q/chain-length @db)))
  (is (= 3 (count (q/unspent-outputs address-a @db))))
  (is (= 75 (q/balance address-a @db))))

(deftest test-selecting-sources-from-output-pool
  (let [pool [{:amount 25 :address 1234}
              {:amount 14 :address 1234}]
        sources (txn/select-sources 25 pool)]
    (is (= (take 1 pool) sources)))
  (let [pool [{:amount 11 :address 1234}
              {:amount 14 :address 1234}]
        sources (txn/select-sources 25 pool)]
    (is (= pool sources))))

(deftest test-generating-payment-fails-without-sufficient-funds
  (miner/mine-and-commit-db! db)
  (is (= 1 (count (q/longest-chain @db))))
  (is (= 1 (count (q/unspent-outputs address-a @db))))
  (is (= 25 (q/balance address-a @db)))
  (is (thrown? AssertionError
               (txn/payment key-a
                            address-b
                            26
                            @db))))

(deftest test-generating-raw-payment-txn
  (let [sources (concat (:outputs (txn/coinbase @db address-a))
                        (:outputs (txn/coinbase @db address-b)))
        raw-p (txn/raw-txn 50 "addr" sources)]
    (is (= 2 (count (:inputs raw-p))))
    (is (= (into #{} (vals (:coords (first sources))))
           (into #{} (vals (first (:inputs raw-p))))))
    (is (= (into #{} (vals (:coords (last sources))))
           (into #{} (vals (last (:inputs raw-p))))))))

(deftest test-generating-payment-produces-valid-txn
  (miner/mine-and-commit-db! db)
  (let [p (txn/payment key-a address-b 25 @db)
        sig (-> p :inputs first :signature)]
    (is (= 1 (-> p :inputs count)))
    (is (= 1 (-> p :outputs count)))
    (is (= 25 (->> p :outputs (map :amount) (reduce +))))
    (is (wallet/verify sig (txn/txn-signable p) (:public key-a)))))

(deftest test-generating-payment-from-multiple-inputs
  (dotimes [n 2] (miner/mine-and-commit-db! db))
  (let [p (txn/payment key-a address-b 50 @db)
        sig (-> p :inputs first :signature)]
    (is (= 2 (-> p :inputs count)))
    (is (= 1 (-> p :outputs count)))
    (is (= 50 (reduce + (map :amount (:outputs p)))))
    (is (wallet/verify sig (txn/txn-signable p) (:public key-a)))))

(deftest test-generating-payment-with-transaction-fee
  (miner/mine-and-commit-db! db)
  (let [p (txn/payment key-a address-b 24 @db 1)
        sig (-> p :inputs first :signature)]
      (is (= 1 (count (:inputs p))))
      (is (= 1 (count (:outputs p))))
      (is (= 24 (reduce + (map :amount (:outputs p)))))
      (is (= 25 (->> p
                     :inputs
                     (map (partial q/source-output @db))
                     (map :amount)
                     (reduce +))))
      (is (wallet/verify sig (txn/txn-signable p) (:public key-a)))))

(deftest test-generating-payment-with-change
  (miner/mine-and-commit-db! db)
  (let [p (txn/payment key-a address-b 15 @db 3)
        sig (:signature (first (:inputs p)))]
    (is (= 1 (count (:inputs p))))
    (is (= 2 (count (:outputs p))))
    (is (= 15 (:amount (first (:outputs p)))))
    (is (= address-b (:address (first (:outputs p)))))
    (is (= 7 (:amount (last (:outputs p)))))
    (is (= address-a (:address (last (:outputs p)))))
    (is (= 22 (reduce + (map :amount (:outputs p)))))
    (is (= 25 (->> p
                   :inputs
                   (map (partial q/source-output @db))
                   (map :amount)
                   (reduce +))))
    (is (wallet/verify sig (txn/txn-signable p) (:public key-a)))))

(deftest test-generating-unsigned-payment
  (miner/mine-and-commit-db! db)
  (let [p (txn/unsigned-payment address-a address-b 15 @db 3)
        sig (:signature (first (:inputs p)))]
      (is (= 1 (count (:inputs p))))
      (is (= 2 (count (:outputs p))))
      (is (= 15 (:amount (first (:outputs p)))))
      (is (= address-b (:address (first (:outputs p)))))
      (is (= 7 (:amount (last (:outputs p)))))
      (is (= address-a (:address (last (:outputs p)))))
      (is (= 22 (reduce + (map :amount (:outputs p)))))
      (is (= 25 (->> p
                     :inputs
                     (map (partial q/source-output @db))
                     (map :amount)
                     (reduce +))))
      (is (= nil sig))
      ;; verify that we can subsequently sign the txn as needed
      (let [signed (txn/sign-txn p (:private key-a))]
        (is (wallet/verify (:signature (first (:inputs signed)))
              (txn/txn-signable signed)
              (:public key-a))))))
