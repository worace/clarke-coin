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

(def db-path (str "/tmp/" (th/dashed-ns-name)))
(th/clear-db-path! db-path)
(with-open [test-conn (db/conn db-path)]
  (def db (atom (db/db-map test-conn)))
  (def key-a (wallet/generate-keypair 512))
  (def key-b (wallet/generate-keypair 512))

  (def a-coinbase (txn/coinbase (:address key-a) @db))
  (def b-coinbase (txn/coinbase (:address key-b) @db))

  (def a-paid (blocks/generate-block [a-coinbase] @db))
  (miner/mine-and-commit-db! db a-paid)
  (def b-paid (blocks/generate-block [b-coinbase] @db))
  ;; A and B both start with 25
  ;; A: 25 B: 25
  (miner/mine-and-commit-db! db b-paid)
  (def b-pays-a-5 (txn/payment key-b
                               (:address key-a)
                               5
                               @db))

  ;; B pays A 5
  ;; A: 30, B: 20
  (swap! db q/add-block (miner/mine (blocks/generate-block [b-pays-a-5] @db)))
  (assert (= 3 (q/chain-length @db)))
  ;; Pending payment transactions:
  (def a-pays-b-15 (txn/payment key-a
                                (:address key-b)
                                15
                                @db))

  (def a-pays-b-50 (assoc-in a-pays-b-15 [:outputs 0 :amount] 50))


  (deftest test-valid-structure
    (is (txn-structure-valid? @db a-coinbase))
    (is (txn-structure-valid? @db a-pays-b-15))
    (is (not (txn-structure-valid? @db {}))))

  (deftest test-sufficient-balance
    (is (sufficient-inputs? @db a-pays-b-15))
    (is (not (sufficient-inputs? @db a-pays-b-50))))

  (deftest test-all-inputs-have-sources
    (is (inputs-properly-sourced? @db a-pays-b-15))
    (let [bogus-sourced (assoc-in a-pays-b-15
                                  [:inputs 0 :source-hash]
                                  "pizza")]
      (is (not (inputs-properly-sourced? @db bogus-sourced)))))

  (deftest test-valid-signatures
    (is (signatures-valid? @db a-pays-b-15))
    (let [bogus-sig (assoc-in a-pays-b-15
                              [:outputs 0 :amount]
                              16)]
      (is (not (signatures-valid? @db bogus-sig)))))

  (deftest test-inputs-unspent
    (is (inputs-unspent? @db a-pays-b-15))
    (is (= 3 (q/chain-length @db)))
    (is (= 3 (count (mapcat :transactions (q/longest-chain @db)))))
    (is (contains? (->> (q/longest-chain @db)
                        (mapcat :transactions)
                        (into #{}))
                   b-pays-a-5))
    ;; ^ b-pays-a-5 is already in the chain
    ;; so its transaction inputs should show as having already
    ;; been spent
    (is (not (inputs-unspent? @db b-pays-a-5))))

  (deftest test-correct-hash
    (is (valid-hash? @db a-pays-b-15))
    (is (not (valid-hash? @db (assoc a-pays-b-15 :hash "pizza")))))

  (run-all-tests #"transaction-validations-test")

  )
