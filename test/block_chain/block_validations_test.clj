(ns block-chain.block-validations-test)
(ns block-chain.block-validations-test
  (:require [clojure.test :refer :all]
            [block-chain.wallet :as wallet]
            [clojure.pprint :refer [pprint]]
            [block-chain.utils :refer :all]
            [block-chain.db :refer [empty-db]]
            [block-chain.queries :as q]
            [block-chain.miner :as miner]
            [block-chain.transactions :as txn]
            [block-chain.blocks :as blocks]
            [block-chain.block-validations :refer :all]))

(def db (atom empty-db))
(def key-a (wallet/generate-keypair 512))
(def key-b (wallet/generate-keypair 512))

(def a-coinbase (txn/coinbase (:address key-a) @db))
(def b-coinbase (txn/coinbase (:address key-b) @db))

(->> (blocks/generate-block [a-coinbase] @db)
     (miner/mine)
     (swap! db q/add-block))

;; A pays B 5
(def a-pays-b-5 (txn/payment key-a
                             (:address key-b)
                             5
                             @db
                             2))

(q/add-transaction-to-pool! db a-pays-b-5)

;; Block contains 5 A -> B
;; And 25 coinbase -> A
(def un-mined-block (-> @db
                        (txn/txns-for-next-block (:address key-a))
                        (blocks/generate-block @db)
                        (blocks/hashed)))

(def block-0 (last (q/longest-chain @db)))

(deftest test-valid-block-hash
  (is (valid-hash? @db block-0))
  (is (not (valid-hash? @db (assoc-in block-0 [:header :hash] "pizza")))))

(deftest test-valid-parent-hash
  (is (valid-parent-hash? @db un-mined-block))
  ;; Genesis Blocks doesn't validate because its parent
  ;; is not in the DB
  (is (not (valid-parent-hash? @db block-0))))

(deftest test-hash-meets-target
  (is (hash-meets-target? @db block-0))
  (is (not (hash-meets-target? @db un-mined-block))))

(defn hex-* [i hex]
  (-> hex
      hex->int
      (* i)
      hex-string))

(deftest test-block-target-within-threshold
  (is (valid-target? @db block-0))
  (is (valid-target? @db un-mined-block))
  (is (valid-target? @db (update-in un-mined-block
                                [:header :target]
                                (partial hex-* 1001/1000))))
  (is (not (valid-target? @db (update-in un-mined-block
                                        [:header :target]
                                        (partial hex-* 5))))))

(deftest test-valid-txn-hash
  (is (valid-txn-hash? @db un-mined-block))
  (is (not (valid-txn-hash? @db (assoc-in un-mined-block
                                      [:transactions 0 :hash]
                                      "pizza")))))

(deftest test-valid-coinbase
  (is (valid-coinbase? @db un-mined-block))
  (is (not (valid-coinbase? @db (update-in un-mined-block
                                          [:transactions 0 :outputs 0 :amount]
                                          inc))))
  (with-redefs [txn/coinbase-reward 15]
    (is (not (valid-coinbase? @db un-mined-block)))))

(deftest test-valid-timestamp
  (let [b (blocks/hashed
           (blocks/generate-block
            [(txn/coinbase (:address key-a) @db)
             a-pays-b-5]
            @db))]
    (is (valid-timestamp? @db b))
    (is (not (valid-timestamp? @db (assoc-in b
                                            [:header :timestamp]
                                            (+ (current-time-millis) 7000)))))
    (is (not (valid-timestamp? @db (assoc-in b
                                            [:header :timestamp]
                                            (- (current-time-millis)
                                               70000)))))))

(deftest test-all-txns-valid
  (is (valid-transactions? @db un-mined-block))
  (is (not (valid-transactions? @db (update-in un-mined-block
                                              [:transactions 1 :outputs 0 :amount]
                                              inc)))))

(deftest test-no-transactions-spend-same-inputs
  (is (unique-txn-inputs? @db un-mined-block))
  (let [duped-txn (get-in un-mined-block [:transactions 1])]
    (is (not (unique-txn-inputs? @db (update-in un-mined-block
                                            [:transactions]
                                            #(conj % duped-txn)))))))

(deftest test-validate-whole-block
  (is (empty? (validate-block @db (miner/mine un-mined-block)))))
