(ns block-chain.http-test
  (:require [clojure.test :refer :all]
            [clj-http.client :as http]
            [block-chain.http :as server]
            [block-chain.db :as db]
            [block-chain.utils :refer :all]
            [clojure.pprint :refer [pprint]]))

(def base-url "http://localhost:9292")
(defn post-req [path data]
  (update
   (http/post (str base-url path)
              {:form-params data
               :content-type :json})
   :body
   read-json))

(defn get-req [path]
  (update
   (http/get (str base-url path))
   :body
   read-json))

(defn with-server [f]
  (server/start! 9292)
  (f)
  (server/stop!))

(defn with-db [f]
  (reset! db/peers #{})
  (reset! db/transaction-pool #{})
  (reset! db/block-chain [])
  (f)
  (reset! db/peers #{})
  (reset! db/transaction-pool #{})
  (reset! db/block-chain []))

(use-fixtures :once with-server)
(use-fixtures :each with-db)

(deftest test-echo
  (let [r (post-req "/echo" {:hello "there"})]
    (is (= {:message "echo" :payload {:hello "there"}}
           (:body r)))
    (is (= "application/json; charset=utf-8"
           (get-in r [:headers "Content-Type"])))))

(deftest test-ping
  (let [r (post-req "/ping" {:hello "there"})]
    (is (= {:message "pong" :payload {:hello "there"}}
           (:body r)))))

(deftest test-get-peers
  (swap! db/peers conj "pizza")
  (is (= {:message "peers" :payload ["pizza"]} (:body (get-req "/peers")))))

(deftest test-get-balance
  (is (= {:message "balance" :payload {:address "pizza" :balance 0} } (:body (post-req "/balance" {:address "pizza"})))))

(deftest test-get-blocks
  (swap! db/block-chain conj {:data "stuff"})
  (is  (= {:message "blocks" :payload [{:data "stuff"}]}
          (:body (get-req "/blocks")))))

(deftest test-get-transaction-pool
  (swap! db/transaction-pool conj {:data "stuff"})
  (is  (= {:message "transaction_pool" :payload [{:data "stuff"}]}
          (:body (get-req "/pending_transactions")))))

(deftest test-get-block-info
  (swap! db/block-chain conj {:header {:hash "pizza"}})
  (is  (= {:message "block_info" :payload {:header {:hash "pizza"}}}
          (:body (get-req "/blocks/pizza")))))

(deftest test-get-latest-block
  (swap! db/block-chain conj {:header {:hash "pizza"}})
  (is  (= {:message "latest_block" :payload {:header {:hash "pizza"}}}
          (:body (get-req "/latest_block")))))

(deftest test-get-block-height
  (swap! db/block-chain conj {})
  (is  (= {:message "block_height" :payload 1}
          (:body (get-req "/block_height")))))

(deftest test-get-block-info
  (swap! db/block-chain conj {:transactions [{:hash "calzone"}]})
  (is  (= {:message "transaction_info" :payload {:hash "calzone"}}
          (:body (get-req "/transactions/calzone")))))

(deftest test-submit-transaction
  (post-req "/pending_transactions" {:some "txn"})
  (is  (= {:message "transaction_pool" :payload [{:some "txn"}]}
          (:body (get-req "/pending_transactions")))))

(deftest test-submit-block
  (post-req "/blocks" {:some "block"})
  (is  (= {:message "blocks" :payload [{:some "block"}]}
          (:body (get-req "/blocks")))))
