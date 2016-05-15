(ns block-chain.integration-test
  (:require [clojure.test :refer :all]
            [clojure.tools.namespace.repl :refer [refresh]]
            [clojure.math.numeric-tower :as math]
            [block-chain.chain :as bc]
            [block-chain.utils :refer :all]
            [block-chain.wallet :as wallet]
            [block-chain.key-serialization :as ks]
            [block-chain.transactions :as txn]
            [block-chain.blocks :as blocks]
            [block-chain.db :as db :refer [empty-db]]
            [block-chain.queries :as q]
            [block-chain.miner :as miner]))

(def key-a wallet/keypair)
(def address-a (:address wallet/keypair))
(def key-b (wallet/generate-keypair 512))
(def address-b (:address key-b))

(defn db-with-blocks [n]
  (reduce (fn [db _] (miner/mine-and-commit-db! db))
          (atom db/empty-db)
          (range n)))

(deftest generating-coinbase
  (let [cb (miner/coinbase address-a)]
    (is (= [:inputs :outputs :timestamp :hash] (keys cb)))
    (is (= (:hash cb)
           (get-in cb [:outputs 0 :coords :transaction-id])))))

(deftest test-mining-block
  (let [block (-> (miner/coinbase address-a)
                  (blocks/generate-block-db {:db empty-db})
                  (miner/mine))]
    (is (blocks/meets-target? block))))

(deftest test-committing-block-to-chain
  (let [db (atom db/empty-db)]
    (is (= 0 (q/chain-length @db)))
    (miner/mine-and-commit-db! db
                              (blocks/generate-block-db
                               [(miner/coinbase address-a)]
                               {:db @db}))
    (is (= 1 (q/chain-length @db)))))

(deftest test-mining-multiple-blocks
  (let [db (db-with-blocks 4)]
    (is (= 4 (q/chain-length @db)))
    (is (= (q/phash (first (q/longest-chain @db)))
           (q/bhash (second (q/longest-chain @db)))))
    (is (= (q/phash (first (drop 1 (q/longest-chain @db))))
           (q/bhash (second (drop 1 (q/longest-chain @db))))))
    (is (= (q/phash (first (drop 2 (q/longest-chain @db))))
           (q/bhash (second (drop 2 (q/longest-chain @db))))))))

(deftest test-checking-balances
  (let [db (atom db/empty-db)]
    (dotimes [n 3] (miner/mine-and-commit-db! db))
    (is (= 3 (q/chain-length @db)))
    (is (= 3 (count (bc/unspent-outputs address-a (q/longest-chain @db)))))
    (is (= 75 (bc/balance address-a (q/longest-chain @db))))))

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
  (let [db (db-with-blocks 1)]
    (is (= 1 (count (q/longest-chain @db))))
    (is (= 1 (count (bc/unspent-outputs address-a (q/longest-chain @db)))))
    (is (= 25 (bc/balance address-a (q/longest-chain @db))))
    (is (thrown? AssertionError
                 (miner/generate-payment key-a
                                         address-b
                                         26
                                         (q/longest-chain @db))))))

(deftest test-generating-raw-payment-txn
  (let [sources (concat (:outputs (miner/coinbase address-a))
                        (:outputs (miner/coinbase address-b)))
        raw-p (miner/raw-payment-txn 50 "addr" sources)]
    (is (= 2 (count (:inputs raw-p))))
    (is (= (into #{} (vals (:coords (first sources))))
           (into #{} (vals (first (:inputs raw-p))))))
    (is (= (into #{} (vals (:coords (last sources))))
           (into #{} (vals (last (:inputs raw-p))))))))

(deftest test-generating-payment-produces-valid-txn
  (let [db (db-with-blocks 1)
        p (miner/generate-payment key-a
                                  address-b
                                  25
                                  (q/longest-chain @db))
        sig (-> p :inputs first :signature)]
    (is (= 1 (-> p :inputs count)))
    (is (= 1 (-> p :outputs count)))
    (is (= 25 (->> p :outputs (map :amount) (reduce +))))
    (is (wallet/verify sig (txn/txn-signable p) (:public key-a)))))

(deftest test-generating-payment-from-multiple-inputs
  (let [db (db-with-blocks 2)
        p (miner/generate-payment key-a
                                  address-b
                                  50
                                  (q/longest-chain @db))
        sig (-> p :inputs first :signature)]
    (is (= 2 (-> p :inputs count)))
    (is (= 1 (-> p :outputs count)))
    (is (= 50 (reduce + (map :amount (:outputs p)))))
    (is (wallet/verify sig (txn/txn-signable p) (:public key-a)))))

(deftest test-generating-payment-with-transaction-fee
  (let [db (db-with-blocks 1)
        p (miner/generate-payment key-a address-b 24 (q/longest-chain @db) 1)
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
  (let [db (db-with-blocks 1)
        p (miner/generate-payment key-a address-b 15 (q/longest-chain @db) 3)
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
                   (map (partial bc/source-output (q/longest-chain @db)))
                   (map :amount)
                   (reduce +))))
    (is (wallet/verify sig (txn/txn-signable p) (:public key-a)))))

(deftest test-generating-unsigned-payment
  (let [db (db-with-blocks 1)
        p (miner/generate-unsigned-payment address-a address-b 15 (q/longest-chain @db) 3)
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
                     (map (partial bc/source-output (q/longest-chain @db)))
                     (map :amount)
                     (reduce +))))
      (is (= nil sig))
      ;; verify that we can subsequently sign the txn as needed
      (let [signed (wallet/sign-txn p (:private key-a))]
        (is (wallet/verify (:signature (first (:inputs signed)))
              (txn/txn-signable signed)
              (:public key-a))))))

(deftest test-utxos-on-big-chain)
