(ns block-chain.http-test
  (:require [clojure.test :refer :all]
            [clj-http.client :as http]
            [block-chain.http :as server]
            [org.httpkit.server :as httpkit]
            [block-chain.db :as db]
            [block-chain.queries :as q]
            [block-chain.peer-client :as pc]
            [block-chain.blocks :as blocks]
            [block-chain.transactions :as txn]
            [block-chain.wallet :as wallet]
            [block-chain.miner :as miner]
            [block-chain.utils :refer :all]
            [block-chain.schemas :refer :all]
            [block-chain.target :as target]
            [block-chain.test-helper :as th]
            [schema.core :as s]
            [clojure.pprint :refer [pprint]]))

(def base-url "http://localhost:9292")

(def key-a wallet/keypair)
(def key-b (wallet/generate-keypair 512))

(defn next-block [db] (miner/mine (miner/next-block db)))

(defn sample-transaction [db]
  (txn/payment key-a (:address key-b) 15 db))

(defn with-server [f]
  (server/start! 9292)
  (f)
  (server/stop!))

(defn with-peer [f]
  (let [shutdown-fn (httpkit/run-server (th/baby-peer (atom {}))
                                        {:port 9293})]
    (try
      (f)
      (finally (shutdown-fn)))))

(defn with-db [f]
  (with-open [conn (th/temp-db-conn)]
    (reset! db/db (db/db-map conn))
    (miner/mine-and-commit-db! db/db)
    (f)))

(use-fixtures :once with-server with-peer)
(use-fixtures :each with-db)

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
  (q/add-peer! db/db {:host "127.0.0.1" :port 9999})
  (is (= {:message "peers" :payload [{:host "127.0.0.1" :port 9999}]}
         (:body (get-req "/peers")))))

(deftest test-get-balance
  (is (= {:message "balance" :payload {:address "pizza" :balance 0} }
         (:body (post-req "/balance" {:address "pizza"})))))

(deftest test-get-blocks
  (is (= {:message "blocks" :payload (vec (reverse (q/longest-chain @db/db)))}
          (:body (get-req "/blocks")))))

(deftest test-get-transaction-pool
  (q/add-transaction-to-pool! db/db (sample-transaction @db/db))
  (is  (= {:message "transaction_pool"
           :payload (into [] (q/transaction-pool @db/db))}
          (:body (get-req "/pending_transactions")))))

(deftest test-get-block-info
  (is (= {:message "block_info" :payload (q/highest-block @db/db)}
         (:body (get-req (str "/blocks/" (q/highest-hash @db/db)))))))

(deftest test-get-latest-block
  (is  (= {:message "latest_block" :payload (q/highest-block @db/db)}
          (:body (get-req "/latest_block")))))

(deftest test-get-block-height
  (is (= 1 (pc/block-height {:host "localhost" :port "9292"}))))

(deftest test-submit-transaction
  (let [txn (sample-transaction @db/db)]
    (post-req "/pending_transactions" txn)
    (is  (= {:message "transaction_pool" :payload [txn]}
            (:body (get-req "/pending_transactions"))))))

(deftest test-submitting-invalid-transaction-returns-validation-errors
  (let [resp (post-req "/pending_transactions"
                       (update-in (sample-transaction @db/db)
                                  [:outputs 0 :amount]
                                  inc))]
    (is (= "transaction-rejected" (:message (:body resp))))
    (is (= 400 (:status resp)))
    (is (= (sort ["Transaction lacks sufficient inputs to cover its outputs.",
                  "One or more transactions signatures is invalid."
                  "Transaction's hash does not match its contents."])
           (sort (:payload (:body resp)))))))

(deftest test-submit-valid-block
  (let [b (next-block @db/db)
        resp (post-req "/blocks" b)]
    (is (= 200 (:status resp)))
    (is (= "block-accepted" (:message (:body resp))))
    (is (= b (:payload (:body resp))))
    (is (= {:message "blocks" :payload [(q/root-block @db/db) b]}
           (:body (get-req "/blocks"))))))

(deftest test-submitting-invalid-block-returns-validation-errors
  (let [b (next-block @db/db)
        tweaked (update-in b [:transactions 0 :outputs 0 :amount] inc)
        resp (post-req "/blocks" tweaked)]
    (is (= "block-rejected" (:message (:body resp))))
    (is (= 400 (:status resp)))
    (is (= (sort ["Block's coinbase transaction is malformed or has incorrect amount."])
           (sort (:payload (:body resp)))))))

(deftest test-getting-blocks-since-height
  (miner/mine-and-commit-db!)
  (miner/mine-and-commit-db!)
  (is (= (map q/bhash (drop 1 (reverse (q/longest-chain @db/db))))
         (pc/blocks-since {:host "localhost" :port "9292"}
                          (q/bhash (last (q/longest-chain @db/db)))))))

(deftest test-adding-peer-via-http
  (pc/send-peer {:host "127.0.0.1" :port 9292} 9293)
  (is (= [{:host "127.0.0.1" :port 9293}]
         (q/peers @db/db))))

(deftest test-doesnt-add-peer-that-is-not-online
  (pc/send-peer {:host "127.0.0.1" :port 9292} 9294)
  (is (= []
         (q/peers @db/db))))

(deftest test-gets-static-html-route
  (let [r (-> "http://localhost:9292/graph"
              (http/get {:throw-exceptions false}))]
    (is (= 200 (:status r)))))

(deftest test-requests-unmined-block-for-key
  (miner/mine-and-commit-db!)
  (with-redefs [target/default target/hard]
   (let [addr (:address (wallet/generate-keypair 512))
         resp (pc/unmined-block {:host "localhost"
                                 :port "9292"}
                                addr)]
     (is (= addr (get-in resp [:transactions 0 :outputs 0 :address])))
     (is (= ["Block's hash does not meet the specified target."]
            (pc/send-block {:host "localhost" :port "9292"} resp))))))

(deftest test-uses-default-key-if-none-provided
  (miner/mine-and-commit-db!)
  (with-redefs [target/default target/hard]
    ;; send with explicit nil address
    (let [resp (pc/unmined-block {:host "localhost"
                                  :port "9292"}
                                 nil)]
      (is (= (q/wallet-addr @db/db) (get-in resp [:transactions 0 :outputs 0 :address])))
      (is (= ["Block's hash does not meet the specified target."]
             (pc/send-block {:host "localhost" :port "9292"} resp))))
    ;; send with empty body
    (let [resp (pc/unmined-block {:host "localhost"
                                  :port "9292"})]
      (is (= (q/wallet-addr @db/db) (get-in resp [:transactions 0 :outputs 0 :address])))
      (is (= ["Block's hash does not meet the specified target."]
             (pc/send-block {:host "localhost" :port "9292"} resp))))))
