(ns block-chain.message-handlers-test
  (:require [clojure.test :refer :all]
            [block-chain.utils :refer :all]
            [clojure.pprint :refer [pprint]]
            [block-chain.wallet :as wallet]
            [block-chain.miner :as miner]
            [block-chain.transactions :as txn]
            [org.httpkit.server :as httpkit]
            [compojure.core :refer [routes]]
            [block-chain.db :as db]
            [block-chain.queries :as q]
            [schema.core :as s]
            [block-chain.schemas :refer :all]
            [block-chain.blocks :as blocks]
            [clj-http.client :as http]
            [block-chain.message-handlers :refer :all]))

(defn responds
  ([val msg] (responds val msg {}))
  ([val msg sock-info]
   (let [resp (handler msg sock-info)]
     (is (= val (:payload resp))))))

(defn response
  ([msg] (response msg {}))
  ([msg sock-info] (:payload (handler msg sock-info))))

(def test-port 9292)
(def sock-info
  {:remote-address "127.0.0.1"
   :local-port test-port
   :outgoing-port 51283})

(def peer-requests (atom {}))
(defn peer-handler [req]
  (let [req (if (:body req)
              (assoc req :body (-> req :body .bytes slurp))
              req)]
    (swap! peer-requests update (:uri req) conj req)
    {:status 200}))

(defn with-peer [f]
  (reset! peer-requests {})
  (let [shutdown-fn (httpkit/run-server (routes peer-handler)
                                        {:port test-port})]
    (try
      (f)
      (finally (do (shutdown-fn)
                   (reset! peer-requests {}))))))

(defn with-db [f]
  (reset! db/db db/initial-db)
  (f)
  (reset! db/db db/initial-db))

(use-fixtures :each with-db with-peer)

(deftest test-echo
  (let [msg {:message "echo"
             :payload "echo this"}]
    (is (= msg (handler msg {})))))

(deftest test-ping-pong
  (let [msg {:message "ping"
             :payload (current-time-seconds)}]
    (is (= (assoc msg :message "pong") (handler msg {})))))

(deftest test-getting-adding-and-removing-peers
  (is (= [] (response {:message "get_peers"})))
  (handler {:message "add_peer"
            :payload {:port test-port}}
           sock-info)
  (is (= [{:host "127.0.0.1" :port test-port}]
         (response {:message "get_peers"})))
  (handler {:message "remove_peer"
            :payload {:port test-port}}
           sock-info)
  (is (= [] (response {:message "get_peers"}))))

(deftest test-getting-balance-for-key
  (miner/mine-and-commit-db!)
  (responds {:balance 25 :address (:address wallet/keypair)}
            {:message "get_balance" :payload (:address wallet/keypair)}))

(deftest test-getting-block-height
  (responds 1 {:message "get_block_height"})
  (miner/mine-and-commit-db!)
  (responds 2 {:message "get_block_height"}))

(deftest test-getting-latest-block
  (responds db/genesis-block {:message "get_latest_block"})
  (miner/mine-and-commit-db!)
  (responds (q/highest-block @db/db)
            {:message "get_latest_block"}))

(deftest test-generating-transaction
  (miner/mine-and-commit-db!)
  (let [key-b (wallet/generate-keypair 512)
        utxn (:payload (handler {:message "generate_payment"
                                 :payload {:amount 15
                                           :from-address (:address wallet/keypair)
                                           :to-address (:address key-b)
                                           :fee 3}}
                                sock-info))]
    (is (= 1 (count (:inputs utxn))))
    (is (nil? (get-in utxn [:inputs 0 :signature])))
    (is (= 2 (count (:outputs utxn))))
    ;; verify diff b/t inputs and outputs accounts for fee
    (let [source (q/source-output @db/db
                                  (-> utxn :inputs first))]
      (is (= 3 (- (:amount source)
                  (reduce + (map :amount (:outputs utxn)))))))))

;; TODO
#_(deftest test-submitting-and-mining-transaction
  (miner/mine-and-commit-db!)
  (let [key-b (wallet/generate-keypair 512)
        payment (miner/generate-payment wallet/keypair (:address key-b) 25 (q/longest-chain @db/db))]
    (is (= payment (s/validate Transaction payment)))
    (is (= 25 (bc/balance-db (:address wallet/keypair)
                             @db/db)))
    (is (= 1 (count (:outputs payment))))
    (is (= {:message "transaction-accepted"
            :payload payment}
           (handler {:message "submit_transaction"
                     :payload payment}
                    sock-info)))
    (is (= 1 (count (q/transaction-pool @db/db))))
    (miner/mine-and-commit-db!)
    (is (empty? (q/transaction-pool @db/db)))
    (is (= 25 (bc/balance-db (:address wallet/keypair)
                             @db/db)))
    (is (= 25 (bc/balance-db (:address key-b) @db/db)))))

;; TODO
#_(deftest test-submitting-transaction-with-txn-fee
  (let [key-b (wallet/generate-keypair 512)]
    (is (= 0 (bc/balance-db (:address wallet/keypair) @db/db)))
    (miner/mine-and-commit-db!)
    (let [payment (miner/generate-payment wallet/keypair (:address key-b) 24 (q/longest-chain @db/db) 1)]
      (handler {:message "submit_transaction" :payload payment} sock-info)
      (miner/mine-and-commit-db!)
      ;; miner should have 25 from coinbase and 1 from allotted txn fee
      (is (= 2 (q/block-height @db/db)))
      (is (= 1 ()))
      (is (= 26 (bc/balance-db (:address wallet/keypair) @db/db)))
      ;; B should have 24 from the payment
      (is (= 24 (bc/balance-db (:address key-b) @db/db))))))

(deftest test-only-forwards-new-transactions
  (miner/mine-and-commit-db!)
  (is (empty? (q/transaction-pool @db/db)))
  (let [txn (txn/payment wallet/keypair (:address wallet/keypair) 25 @db/db)]
    (handler {:message "add_peer" :payload {:port test-port}} sock-info)
    ;; send same txn twice but should only get forwarded once
    (is (= {:message "transaction-accepted" :payload txn}
           (handler {:message "submit_transaction" :payload txn} sock-info)))
    (is (= "transaction-rejected"
           (:message (handler {:message "submit_transaction" :payload txn} sock-info))))
    (is (= 1 (count (@peer-requests "/pending_transactions"))))
    (let [req (first (@peer-requests "/pending_transactions"))]
      (is (= :post (:request-method req)))
      (is (= txn (read-json (:body req)))))))

(defn json-body [req] (read-json (:body req)))
(deftest test-forwarding-mined-blocks-to-peers
  (q/add-peer! db/db {:port test-port :host "127.0.0.1"})
  (miner/mine-and-commit-db!)
  (is (= 2 (q/chain-length @db/db)))
  (is (= 1 (count (mapcat val @peer-requests))))
  (is (= (list "/blocks") (keys @peer-requests)))
  (let [req (first (@peer-requests "/blocks"))]
    (is (= :post (:request-method req)))
    (is (= (q/highest-block @db/db)
           (json-body req)))))

(deftest test-receiving-new-block-adds-to-block-chain
  (let [b (miner/mine (blocks/generate-block [(txn/coinbase)]
                                             @db/db))]
    (is (= b (:payload (handler {:message "submit_block" :payload b} sock-info))))
    (is (= 2 (q/chain-length @db/db)))))

(deftest test-forwarding-received-blocks-to-peers
  (q/add-peer! db/db {:port test-port :host "127.0.0.1"})
  (let [b (miner/mine (blocks/generate-block [(txn/coinbase)]
                                             @db/db))]
    (handler {:message "submit_block" :payload b} sock-info)
    (is (= 2 (q/chain-length @db/db)))
    (is (= (list "/blocks") (keys @peer-requests)))
    (let [req (first (@peer-requests "/blocks"))]
      (is (= :post (:request-method req)))
      (is (= b (json-body req)))
      (is (= (q/highest-block @db/db)
             (json-body req))))))

(deftest test-forwards-received-block-to-peers-only-if-new
  (q/add-peer! db/db {:port test-port :host "127.0.0.1"})
  (let [b (miner/mine (blocks/generate-block [(txn/coinbase)]
                                             @db/db))]
    (is (= b (:payload (handler {:message "submit_block" :payload b} sock-info))))
    (is (= 2 (q/chain-length @db/db)))
    (handler {:message "submit_block" :payload b} sock-info)
    (is (= 2 (q/chain-length @db/db)))
    (let [req (first (@peer-requests "/blocks"))]
      (is (= :post (:request-method req)))
      (is (= b (json-body req)))
      (is (= 1 (count (mapcat val @peer-requests))))
      (is (= (q/highest-block @db/db)
             (json-body req))))))

(deftest test-receiving-block-clears-txn-pool
  (miner/mine-and-commit-db!)
  (let [chain (q/longest-chain @db/db)
        addr (:address wallet/keypair)
        txn (txn/payment wallet/keypair addr 25 @db/db)
        b (-> (txn/txns-for-next-block @db/db addr [txn])
              (blocks/generate-block @db/db)
              (miner/mine))]
    (handler {:message "submit_transaction" :payload txn} sock-info)
    (is (= 1 (count (q/transaction-pool @db/db))))
    (responds {:balance 25 :address (:address wallet/keypair)}
              {:message "get_balance" :payload (:address wallet/keypair)})
    (is (= b (:payload (handler {:message "submit_block" :payload b} sock-info))))
    (is (= 0 (count (q/transaction-pool @db/db))))))

(deftest test-validating-incoming-transactions
  (let [alt-db (atom db/empty-db)]
    (miner/mine-and-commit-db! alt-db)
    (let [txn (txn/payment wallet/keypair
                           (:address wallet/keypair)
                           25
                           @alt-db)]
      (is (= "transaction-rejected"
             (:message (handler {:message "submit_transaction" :payload txn} sock-info)))))))

(deftest test-blocks-since
  (miner/mine-and-commit-db!)
  (miner/mine-and-commit-db!)
  (miner/mine-and-commit-db!)
  (is (= {:message "blocks_since" :payload (map q/bhash (drop 1 (reverse (q/longest-chain @db/db)))) }
         (handler
          {:message "get_blocks_since" :payload (q/bhash (last (q/longest-chain @db/db)))}
          sock-info)))
    (is (= {:message "blocks_since" :payload []}
           (handler
                 {:message "get_blocks_since" :payload "pizza"}
                 sock-info))))
