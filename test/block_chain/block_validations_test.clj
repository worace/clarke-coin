(ns block-chain.block-validations-test)
(ns block-chain.block-validations-test
  (:require [clojure.test :refer :all]
            [block-chain.wallet :as wallet]
            [clojure.pprint :refer [pprint]]
            [block-chain.utils :refer :all]
            [block-chain.db :as db]
            [block-chain.queries :as q]
            [block-chain.miner :as miner]
            [block-chain.test-helper :as th]
            [block-chain.transactions :as txn]
            [block-chain.blocks :as blocks]
            [block-chain.block-validations :refer :all]))

(def db-path (str "/tmp/" (th/dashed-ns-name)))
(def db (atom nil))
(def key-a (wallet/generate-keypair 512))
(def key-b (wallet/generate-keypair 512))

(defn a-pays-b-5 [db]
  (txn/payment key-a (:address key-b) 5 db 2))

(defn setup [tests]
  (th/clear-db-path! db-path)
  (reset! db (db/make-db db-path))
  (let [a-coinbase (txn/coinbase @db (:address key-a))]
    (->> (blocks/generate-block [a-coinbase] @db)
         (miner/mine)
         (swap! db q/add-block)))
  (q/add-transaction-to-pool! db (a-pays-b-5 @db))
  (try
    (tests)
    (finally
      (th/close-conns! @db))))
(use-fixtures :once setup)

(defn unmined []
  (-> @db
      (txn/txns-for-next-block (:address key-a))
      (blocks/generate-block @db)
      (blocks/hashed)))

(defn block-0 [db] (last (q/longest-chain db)))

(deftest test-valid-block-hash
  (is (valid-hash? @db (q/root-block @db)))
  (is (not (valid-hash? @db (assoc-in (q/root-block @db)
                                      [:header :hash]
                                      "pizza")))))

(deftest test-valid-parent-hash
  (is (valid-parent-hash? @db (unmined)))
  ;; Genesis Blocks doesn't validate because its parent
  ;; is not in the DB
  (is (not (valid-parent-hash? @db (q/root-block @db)))))

(deftest test-hash-meets-target
  (is (hash-meets-target? @db (q/root-block @db)))
  (is (not (hash-meets-target? @db (unmined)))))

(defn hex-* [i hex]
  (-> hex
      hex->int
      (* i)
      hex-string))

(deftest test-block-target-within-threshold
  (let [b0 (q/root-block @db)]
    (is (valid-target? @db b0))
    (is (valid-target? @db (unmined)))
    (is (valid-target? @db (update-in (unmined)
                                      [:header :target]
                                      (partial hex-* 1001/1000))))
    (is (not (valid-target? @db (update-in (unmined)
                                           [:header :target]
                                           (partial hex-* 5)))))))

(deftest test-valid-txn-hash
  (is (valid-txn-hash? @db (unmined)))
  (is (not (valid-txn-hash? @db (assoc-in (unmined)
                                      [:transactions 0 :hash]
                                      "pizza")))))

(deftest test-valid-coinbase
  (is (valid-coinbase? @db (unmined)))
  (is (not (valid-coinbase? @db (update-in (unmined)
                                          [:transactions 0 :outputs 0 :amount]
                                          inc)))))

(deftest test-valid-timestamp
  (let [b (blocks/hashed
           (blocks/generate-block
            [(txn/coinbase @db (:address key-a))
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
  (is (valid-transactions? @db (unmined)))
  (is (not (valid-transactions? @db (update-in (unmined)
                                              [:transactions 1 :outputs 0 :amount]
                                              inc)))))

(deftest test-no-transactions-spend-same-inputs
  (let [ublock (unmined)
        duped-txn (get-in ublock [:transactions 1])]
    (is (unique-txn-inputs? @db ublock))
    (is (not (unique-txn-inputs? @db (update-in ublock
                                                [:transactions]
                                                #(conj % duped-txn)))))))

(deftest test-validate-whole-block
  (is (empty? (validate-block @db (miner/mine (unmined))))))


;; TODO
#_(deftest test-validates-block-target-against-chain-up-to-that-block)
