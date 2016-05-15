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
  (is (valid-parent-hash? db un-mined-block))
  ;; Genesis Blocks doesn't validate because its parent
  ;; is not in the DB
  (is (not (valid-parent-hash? db block-0))))

(deftest test-hash-meets-target
  (is (hash-meets-target? db block-0))
  (is (not (hash-meets-target? db un-mined-block))))

(defn hex-* [i hex]
  (-> hex
      hex->int
      (* i)
      hex-string))

(deftest test-block-target-within-threshold
  (is (valid-target? db block-0))
  (is (valid-target? db un-mined-block))
  (is (valid-target? db (update-in un-mined-block
                                [:header :target]
                                (partial hex-* 1001/1000))))
  (is (not (valid-target? db (update-in un-mined-block
                                        [:header :target]
                                        (partial hex-* 5))))))

(deftest test-valid-txn-hash
  (is (valid-txn-hash? db un-mined-block))
  (is (not (valid-txn-hash? db (assoc-in un-mined-block
                                      [:transactions 0 :hash]
                                      "pizza")))))

(deftest test-valid-coinbase
  (is (valid-coinbase? db un-mined-block))
  (is (not (valid-coinbase? db (update-in un-mined-block
                                          [:transactions 0 :outputs 0 :amount]
                                          inc))))
  (with-redefs [c/coinbase-reward 15]
    (is (not (valid-coinbase? db un-mined-block)))))

(deftest test-valid-timestamp
  (let [b (blocks/hashed
           (blocks/generate-block
            [(miner/coinbase (:address key-a) [a-pays-b-5] (q/longest-chain db))
             a-pays-b-5]
            {:blocks (q/longest-chain db)}))]
    (is (valid-timestamp? db b))
    (is (not (valid-timestamp? db (assoc-in b
                                            [:header :timestamp]
                                            (+ (current-time-millis) 7000)))))
    (is (not (valid-timestamp? db (assoc-in b
                                            [:header :timestamp]
                                            (- (current-time-millis)
                                               70000)))))))

(deftest test-all-txns-valid
  (is (valid-transactions? db un-mined-block))
  (is (not (valid-transactions? db (update-in un-mined-block
                                              [:transactions 1 :outputs 0 :amount]
                                              inc)))))

(deftest test-no-transactions-spend-same-inputs
  (is (unique-txn-inputs? db un-mined-block))
  (let [duped-txn (get-in un-mined-block [:transactions 1])]
    (is (not (unique-txn-inputs? db (update-in un-mined-block
                                            [:transactions]
                                            #(conj % duped-txn)))))))

(deftest test-validate-whole-block
  (is (empty? (validate-block db (miner/mine un-mined-block)))))
