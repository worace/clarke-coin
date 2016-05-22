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

(defn unmined-cb-block [address])

(defn setup [tests]
  (with-open [conn (th/temp-db-conn)]
    (reset! db (db/db-map conn))
    (miner/mine-and-commit-db! db (miner/next-block @db addr-a))
    (miner/mine-and-commit-db! db (miner/next-block @db addr-b))
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

#_(deftest test-inputs-unspent
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

(run-all-tests #"transaction-validations-test")

;; (def a-paid (blocks/generate-block [a-coinbase] @db))
;; (miner/mine-and-commit-db! db a-paid)
;; (def b-paid (blocks/generate-block [b-coinbase] @db))
;; A and B both start with 25
;; A: 25 B: 25
;; (miner/mine-and-commit-db! db b-paid)
;; (def b-pays-a-5 (txn/payment key-b
;;                              (:address key-a)
;;                              5
;;                              @db))

;; B pays A 5
;; A: 30, B: 20
;; (swap! db q/add-block (miner/mine (blocks/generate-block [b-pays-a-5] @db)))
;; (assert (= 3 (q/chain-length @db)))
;; Pending payment transactions:
;; (def a-pays-b-15 (txn/payment key-a
;;                               (:address key-b)
;;                               15
;;                               @db))

;; (def a-pays-b-50 (assoc-in a-pays-b-15 [:outputs 0 :amount] 50))







  ;; (deftest test-correct-hash
  ;;   (is (valid-hash? @db a-pays-b-15))
  ;;   (is (not (valid-hash? @db (assoc a-pays-b-15 :hash "pizza")))))
