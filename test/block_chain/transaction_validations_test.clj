(ns block-chain.transaction-validations-test
  (:require [clojure.test :refer :all]
            [block-chain.wallet :as wallet]
            [clojure.pprint :refer [pprint]]
            [block-chain.utils :refer :all]
            [block-chain.db :as db]
            [block-chain.queries :as q]
            [clojure.math.numeric-tower :as math]
            [block-chain.transactions :as txn]
            [block-chain.miner :as miner]
            [block-chain.blocks :as blocks]
            [block-chain.test-helper :as th]
            [block-chain.transaction-validations :refer :all]))

(def db (atom nil))
(def key-a (wallet/generate-keypair 512))
(def key-b (wallet/generate-keypair 512))
(def addr-a (:address key-a))
(def addr-b (:address key-b))

(defn setup [tests]
  (with-open [conn (th/temp-db-conn)]
    (reset! db (db/db-map conn))
    (miner/mine-and-commit-db! db (miner/next-block @db addr-a))
    (q/add-transaction-to-pool! db (txn/payment key-a addr-b 10 @db))
    (miner/mine-and-commit-db! db (miner/next-block @db addr-a))
    ;; Ending Balances: A: 40, B: 10
    (tests)))

(use-fixtures :once setup)

(deftest test-valid-structure
  (is (txn-structure-valid? @db (txn/coinbase addr-a @db)))
  (is (txn-structure-valid? @db (txn/payment key-b addr-a 5 @db)))
  (is (not (txn-structure-valid? @db {}))))

(deftest test-sufficient-balance
  (is (sufficient-inputs? @db (txn/payment key-b addr-a 5 @db)))
  (is (not (sufficient-inputs? @db (-> (txn/payment key-b addr-a 5 @db)
                                       (assoc-in [:outputs 0 :amount] 50))))))

(deftest test-all-inputs-have-sources
  (is (inputs-properly-sourced? @db (txn/payment key-a addr-b 15 @db)))
  (is (not (inputs-properly-sourced? @db (-> (txn/payment key-b addr-a 5 @db)
                                             (assoc-in [:inputs 0 :source-hash] "pizza"))))))

(deftest test-valid-signatures
  (is (signatures-valid? @db (txn/payment key-a addr-b 15 @db)))
  (is (not (signatures-valid? @db (-> (txn/payment key-b addr-a 5 @db)
                                      (update-in [:outputs 0 :amount] inc)))))
  (is (not (signatures-valid? @db (-> (txn/payment key-b addr-a 5 @db)
                                      (update-in [:inputs 0 :signature] clojure.string/lower-case))))))

(deftest test-inputs-unspent
  (is (= 2 (q/chain-length @db)))
  ;; 2 Coinbases + 1 Payment A -> B
  (is (= 3 (count (mapcat :transactions (q/longest-chain @db)))))
  ;; 2x Coinbase, 1x Transfer, 1x Change
  (is (= 3 (count (q/utxos @db))))
  (is (= 4 (count (mapcat :outputs (mapcat :transactions (q/longest-chain @db))))))
  (is (= 2 (count (q/unspent-outputs addr-a @db))))
  (is (= 1 (count (q/unspent-outputs addr-b @db))))
  (is (= 10 (q/balance addr-b @db)))
  (is (= 40 (q/balance addr-a @db)))
  (let [p (txn/payment key-a addr-b 15 @db)]
    (is (sources-unspent? @db p)))

  (let [existing-txn (second (:transactions (q/highest-block @db)))]
    (is (not (sources-unspent? @db existing-txn)))
    (is (contains? (->> (q/longest-chain @db)
                      (mapcat :transactions)
                      (into #{}))
                   existing-txn))))

(deftest test-correct-hash
  (is (valid-hash? @db (txn/payment key-a addr-b 15 @db)))
  (is (not (valid-hash? @db (assoc (txn/payment key-a addr-b 15 @db) :hash "pizza"))))
  (is (not (valid-hash? @db (assoc (txn/payment key-a addr-b 15 @db) :min-height 10)))))
