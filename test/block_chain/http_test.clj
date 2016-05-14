(ns block-chain.http-test
  (:require [clojure.test :refer :all]
            [clj-http.client :as http]
            [block-chain.http :as server]
            [block-chain.db :as db]
            [block-chain.queries :as q]
            [block-chain.blocks :as blocks]
            [clojure.math.numeric-tower :as math]
            [block-chain.wallet :as wallet]
            [block-chain.miner :as miner]
            [block-chain.utils :refer :all]
            [block-chain.schemas :refer :all]
            [block-chain.target :as target]
            [schema.core :as s]
            [clojure.pprint :refer [pprint]]))

(def base-url "http://localhost:9292")

(def easy-difficulty (hex-string (math/expt 2 248)))
(def chain (atom []))

(def key-a (wallet/generate-keypair 512))
(def key-b (wallet/generate-keypair 512))

(def a-coinbase (miner/coinbase (:address key-a)))
(def a-paid (blocks/generate-block [a-coinbase]
                                  {:target easy-difficulty}))

(miner/mine-and-commit chain a-paid)
(def sample-block (first @chain))

(def next-block (miner/mine
                 (blocks/generate-block [(miner/coinbase (:address key-a))]
                                        {:target easy-difficulty
                                         :blocks @chain})))

;; A pays B 5
(def sample-transaction (miner/generate-payment key-a
                                         (:address key-b)
                                         15
                                         @chain))

(defn post-req [path data]
  (update
   (http/post (str base-url path)
              {:form-params data
               :throw-exceptions false
               :content-type :json})
   :body
   read-json))

(defn get-req [path]
  (update
   (http/get (str base-url path) {:throw-exceptions false})
   :body
   read-json))

(defn with-server [f]
  (server/start! 9292)
  (f)
  (server/stop!))

(def init-db (-> db/empty-db
                 (q/add-block sample-block)))

(defn with-db [f]
  (reset! db/db init-db)
  (reset! db/peers #{})
  (reset! db/transaction-pool #{})
  (reset! db/block-chain @chain)
  (f)
  (reset! db/db init-db)
  (reset! db/peers #{})
  (reset! db/transaction-pool #{})
  (reset! db/block-chain @chain))

(use-fixtures :once with-server)
(use-fixtures :each with-db)

(deftest test-echo
  (let [r (post-req "/echo" {:message "hi"})]
    (is (= {:message "hi"}
           (:body r)))
    (is (= "application/json; charset=utf-8"
           (get-in r [:headers "Content-Type"])))))

(deftest test-ping
  (let [t (current-time-seconds)
        r (post-req "/ping" {:ping t})]
    (is (= {:pong t}
           (:body r)))))

(deftest test-get-peers
  (swap! db/db q/add-peer {:host "192.168.0.1" :port "3000"})
  (is (= {:message "peers" :payload [{:host "192.168.0.1" :port "3000"}]}
         (:body (get-req "/peers")))))

(deftest test-get-balance
  (is (= {:message "balance" :payload {:address "pizza" :balance 0} } (:body (post-req "/balance" {:address "pizza"})))))

(deftest test-get-blocks
  (is  (= {:message "blocks" :payload @chain}
          (:body (get-req "/blocks")))))

(deftest test-get-transaction-pool
  (swap! db/transaction-pool conj sample-transaction)
  (is  (= {:message "transaction_pool" :payload [sample-transaction]}
          (:body (get-req "/pending_transactions")))))

(deftest test-get-block-info
  (is  (= {:message "block_info" :payload sample-block}
          (:body (get-req (str "/blocks/" (get-in sample-block [:header :hash])))))))

(deftest test-get-latest-block
  (swap! db/block-chain conj sample-block)
  (is  (= {:message "latest_block" :payload sample-block}
          (:body (get-req "/latest_block")))))

(deftest test-get-block-height
  (is  (= {:message "block_height" :payload 1}
          (:body (get-req "/block_height")))))

(deftest test-get-block-info
  (swap! db/block-chain conj sample-block)
  (is  (= {:message "block_info" :payload sample-block}
          (:body (get-req (str "/blocks/" (get-in sample-block [:header :hash])))))))

(deftest test-submit-transaction
  (post-req "/pending_transactions" sample-transaction)
  (is  (= {:message "transaction_pool" :payload [sample-transaction]}
          (:body (get-req "/pending_transactions")))))

(deftest test-submitting-invalid-transaction-returns-validation-errors
  (let [resp (post-req "/pending_transactions" (update-in sample-transaction [:outputs 0 :amount] inc))]
    (is (= "transaction-rejected" (:message (:body resp))))
    (is (= 400 (:status resp)))
    (is (= (sort ["Transaction lacks sufficient inputs to cover its outputs.",
                  "One or more transactions signatures is invalid."
                  "Transaction's hash does not match its contents."])
           (sort (:payload (:body resp)))))))

(deftest test-submit-valid-block
  (let [resp (post-req "/blocks" next-block)]
    (is (= 200 (:status resp)))
    (is (= "block-accepted" (:message (:body resp))))
    (is (= next-block (:payload (:body resp)))))
  (is  (= {:message "blocks" :payload [sample-block next-block]}
          (:body (get-req "/blocks")))))

(deftest test-submitting-invalid-block-returns-validation-errors
  (with-redefs [target/default easy-difficulty]
    (let [resp (post-req "/blocks" (update-in next-block [:transactions 0 :outputs 0 :amount] inc))]
      (is (= "block-rejected" (:message (:body resp))))
      (is (= 400 (:status resp)))
      (is (= (sort ["Block's coinbase transaction is malformed or has incorrect amount."])
             (sort (:payload (:body resp))))))))

(deftest test-getting-blocks-since-height
  (miner/mine-and-commit-db)
  (miner/mine-and-commit-db)
  (is (= (map q/bhash (drop 1 (reverse (q/longest-chain @db/db))))
         (->> @db/db
              q/longest-chain
              last
              q/bhash
              (str "/blocks_since/")
              get-req
              :body
              :payload))))
