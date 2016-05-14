(ns block-chain.block-validations-test)
(ns block-chain.block-validations-test
  (:require [clojure.test :refer :all]
            [block-chain.wallet :as wallet]
            [clojure.pprint :refer [pprint]]
            [block-chain.utils :refer :all]
            [block-chain.db :refer [empty-db]]
            [block-chain.queries :as q]
            [block-chain.miner :as miner]
            [block-chain.blocks :as blocks]
            [block-chain.chain :as c]
            [block-chain.block-validations :refer :all]))

(def key-a (wallet/generate-keypair 512))
(def key-b (wallet/generate-keypair 512))

(def a-coinbase (miner/coinbase (:address key-a)))
(def b-coinbase (miner/coinbase (:address key-b)))

(def a-paid (blocks/generate-block [a-coinbase]))

(def db (->> (blocks/generate-block [a-coinbase])
             (miner/mine)
             (q/add-block empty-db)))

;; A pays B 5
(def a-pays-b-5 (miner/generate-payment key-a
                                        (:address key-b)
                                        5
                                        (q/longest-chain db)
                                        2))

;; Block contains 5 A -> B
;; And 25 coinbase -> A
(def un-mined-block (blocks/hashed
                     (blocks/generate-block
                      [(miner/coinbase (:address key-a) [a-pays-b-5] (q/longest-chain db))
                       a-pays-b-5]
                      {:blocks (q/longest-chain db)})))

(def block-0 (last (q/longest-chain db)))

;; (deftest test-valid-block-hash
;;   (is (valid-hash? block-0 []))
;;   (is (not (valid-hash? (assoc-in a-paid [:header :hash] "pizza") []))))

(deftest test-valid-parent-hash
  (is (valid-parent-hash? un-mined-block db))
  ;; Genesis Blocks doesn't validate because its parent
  ;; is not in the DB
  (is (not (valid-parent-hash? block-0 db))))

(deftest test-hash-meets-target
  (is (hash-meets-target? block-0 []))
  (is (not (hash-meets-target? un-mined-block []))))

(defn hex-* [i hex]
  (-> hex
      hex->int
      (* i)
      hex-string))

(deftest test-block-target-within-threshold
  (is (valid-target? block-0 db))
  (is (valid-target? un-mined-block db))
  (is (valid-target? (update-in un-mined-block
                                [:header :target]
                                (partial hex-* 1001/1000))
                     db))
  (is (not (valid-target? (update-in un-mined-block
                                     [:header :target]
                                     (partial hex-* 5))
                          db))))

(deftest test-valid-txn-hash
  (is (valid-txn-hash? un-mined-block db))
  (is (not (valid-txn-hash? (assoc-in un-mined-block
                                      [:transactions 0 :hash]
                                      "pizza")
                            db))))

(deftest test-valid-coinbase
  (is (valid-coinbase? un-mined-block db))
  (is (not (valid-coinbase? (update-in un-mined-block
                                       [:transactions 0 :outputs 0 :amount]
                                       inc)
                            db)))
  (with-redefs [c/coinbase-reward 15]
    (is (not (valid-coinbase? un-mined-block db)))))

(deftest test-valid-timestamp
  (let [b (blocks/hashed
           (blocks/generate-block
            [(miner/coinbase (:address key-a) [a-pays-b-5] (q/longest-chain db))
             a-pays-b-5]
            {:blocks (q/longest-chain db)}))]
    (is (valid-timestamp? b db))
    (is (not (valid-timestamp? (assoc-in b
                                         [:header :timestamp]
                                         (+ (current-time-millis) 7000))
                               [])))
    (is (not (valid-timestamp? (assoc-in b
                                         [:header :timestamp]
                                         (- (current-time-millis)
                                            70000))
                               [])))))

(deftest test-all-txns-valid
  (is (valid-transactions? un-mined-block db))
  (is (not (valid-transactions? (update-in un-mined-block
                                           [:transactions 1 :outputs 0 :amount]
                                           inc)
                                db))))

(deftest test-no-transactions-spend-same-inputs
  (is (unique-txn-inputs? un-mined-block db))
  (let [duped-txn (get-in un-mined-block [:transactions 1])]
    (is (not (unique-txn-inputs? (update-in un-mined-block
                                            [:transactions]
                                            #(conj % duped-txn))
                                 db)))))

(deftest test-validate-whole-block
  (is (empty? (validate-block (miner/mine un-mined-block) db))))
