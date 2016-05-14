(ns block-chain.message-handlers-test
  (:require [clojure.test :refer :all]
            [block-chain.utils :refer :all]
            [clojure.pprint :refer [pprint]]
            [block-chain.wallet :as wallet]
            [block-chain.miner :as miner]
            [block-chain.chain :as bc]
            [ring.adapter.jetty :as jetty]
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

(defn test-server
  ([port message-fn]
   (let [handler (routes (fn [req]
                           (message-fn (update req :body slurp))
                           {:status 200}))]
     (jetty/run-jetty handler {:port port :join? false}))))

(defmacro with-test-handler
  [bindings & body]
  (cond
    (= (count bindings) 0) `(do ~@body)
    (symbol? (bindings 0)) `(let [~(bindings 0) (test-server test-port ~(bindings 1))]
                              (try
                                (. ~(bindings 0) start)
                                (with-test-handler ~(subvec bindings 2) ~@body)
                                (finally
                                  (. ~(bindings 0) stop))))
    :else (throw (IllegalArgumentException.
                  "with-open only allows Symbols in bindings"))))

(defn with-db [f]
  (reset! db/db db/empty-db)
  (reset! db/transaction-pool #{})
  (f)
  (reset! db/transaction-pool #{})
  (reset! db/db db/empty-db))

(use-fixtures :each with-db)

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
            :payload {:port 8335}}
           sock-info)
  (is (= [{:host "127.0.0.1" :port 8335}]
         (response {:message "get_peers"})))
  (handler {:message "remove_peer"
            :payload {:port 8335}}
           sock-info)
  (is (= [] (response {:message "get_peers"}))))

(deftest test-getting-balance-for-key
  (miner/mine-and-commit-db)
  (responds {:balance 25 :address (:address wallet/keypair)}
            {:message "get_balance" :payload (:address wallet/keypair)}))

(deftest test-getting-block-height
  (responds 0 {:message "get_block_height"})
  (miner/mine-and-commit-db)
  (responds 1 {:message "get_block_height"}))

(deftest test-getting-latest-block
  (responds nil {:message "get_latest_block"})
  (miner/mine-and-commit-db)
  (responds (q/highest-block @db/db)
            {:message "get_latest_block"}))

(deftest test-generating-transaction
  (miner/mine-and-commit-db)
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
    (let [source (bc/source-output (q/longest-chain @db/db)
                                   (-> utxn :inputs first))]
      (is (= 3 (- (:amount source)
                  (reduce + (map :amount (:outputs utxn)))))))))

(deftest test-submitting-and-mining-transaction
  (miner/mine-and-commit-db)
  (let [key-b (wallet/generate-keypair 512)
        payment (miner/generate-payment wallet/keypair (:address key-b) 25 (q/longest-chain @db/db))]
    (is (= payment (s/validate Transaction payment)))
    (is (= 25 (bc/balance (:address wallet/keypair) (q/longest-chain @db/db))))
    (is (= 1 (count (:outputs payment))))
    (is (= {:message "transaction-accepted"
            :payload payment}
           (handler {:message "submit_transaction"
                     :payload payment}
                    sock-info)))
    (is (= 1 (count @db/transaction-pool)))
    (miner/mine-and-commit-db)
    (is (empty? @db/transaction-pool))
    (is (= 25 (bc/balance (:address wallet/keypair)
                          (q/longest-chain @db/db))))
    (is (= 25 (bc/balance (:address key-b) (q/longest-chain @db/db))))))

(deftest test-submitting-transaction-with-txn-fee
  (let [key-b (wallet/generate-keypair 512)]
    (miner/mine-and-commit-db)
    (with-redefs [db/transaction-pool (atom #{})]
      (let [payment (miner/generate-payment wallet/keypair (:address key-b) 24 (q/longest-chain @db/db) 1)]
        (handler {:message "submit_transaction" :payload payment} sock-info)
        (miner/mine-and-commit-db)
        ;; miner should have 25 from coinbase and 1 from allotted txn fee
        (is (= 26 (bc/balance (:address wallet/keypair) (q/longest-chain @db/db))))
        ;; B should have 24 from the payment
        (is (= 24 (bc/balance (:address key-b) (q/longest-chain @db/db))))))))

(deftest test-only-forwards-new-transactions
  (let [reqs (atom [])]
    (with-test-handler [peer (fn [req] (swap! reqs conj req))]
      (miner/mine-and-commit-db)
      (is (= 0 (count @db/transaction-pool)))
      (let [txn (miner/generate-payment wallet/keypair (:address wallet/keypair) 25 (q/longest-chain @db/db))]
        (handler {:message "add_peer" :payload {:port test-port}} sock-info)
        ;; send same txn twice but should only get forwarded once
        (is (= {:message "transaction-accepted" :payload txn}
               (handler {:message "submit_transaction" :payload txn} sock-info)))
        (handler {:message "submit_transaction" :payload txn} sock-info)
        (is (= 1 (get (frequencies (map :uri @reqs)) "/pending_transactions")))
        (let [req (first (filter #(= "/pending_transactions" (:uri %)) @reqs))]
          (is (= :post (:request-method req)))
          (is (= txn (read-json (:body req)))))))))

(defn json-body [req] (read-json (:body req)))
(deftest test-forwarding-mined-blocks-to-peers
  (let [reqs (atom [])]
    (with-test-handler [peer (fn [req] (swap! reqs conj req))]
      (q/add-peer! db/db {:port test-port :host "127.0.0.1"})
      (miner/mine-and-commit-db)
      (is (= 1 (q/chain-length @db/db)))
      (is (= 1 (count @reqs)))
      (is (= "/blocks" (:uri (first @reqs))))
      (is (= :post (:request-method (first @reqs))))
      (is (= (q/highest-block @db/db)
             (json-body (first @reqs)))))))

(deftest test-receiving-new-block-adds-to-block-chain
  (let [b (miner/mine (blocks/generate-block [(miner/coinbase)]))]
    (is (= b (:payload (handler {:message "submit_block" :payload b} sock-info))))
    (is (= 1 (q/chain-length @db/db)))))

(deftest test-forwarding-received-blocks-to-peers
  (let [reqs (atom [])]
    (with-test-handler [peer (fn [req] (swap! reqs conj req))]
      (q/add-peer! db/db {:port test-port :host "127.0.0.1"})
      (let [b (miner/mine (blocks/generate-block [(miner/coinbase)]))]
        (handler {:message "submit_block" :payload b} sock-info)
        (is (= 1 (q/chain-length @db/db)))
        (is (= "/blocks" (:uri (first @reqs))))
        (is (= :post (:request-method (first @reqs))))
        (is (= b (json-body (first @reqs))))
        (is (= 1 (count @reqs)))))))

(deftest test-forwards-received-block-to-peers-only-if-new
  (let [reqs (atom [])]
    (with-test-handler [peer (fn [req] (swap! reqs conj req))]
      (q/add-peer! db/db {:port test-port :host "127.0.0.1"})
      (let [b (miner/mine (blocks/generate-block [(miner/coinbase)]))]
        (is (= b (:payload (handler {:message "submit_block" :payload b} sock-info))))
        (is (= 1 (q/chain-length @db/db)))
        (handler {:message "submit_block" :payload b} sock-info)
        (is (= 1 (q/chain-length @db/db)))
        (is (= "/blocks" (:uri (first @reqs))))
        (is (= :post (:request-method (first @reqs))))
        (is (= b (json-body (first @reqs))))
        (is (= 1 (count @reqs)))))))

(deftest test-receiving-block-clears-txn-pool
  (miner/mine-and-commit-db)
  (let [chain (q/longest-chain @db/db)
        addr (:address wallet/keypair)
        txn (miner/generate-payment wallet/keypair
                                    addr
                                    25
                                    chain)
        b (miner/mine
           (blocks/generate-block
            (miner/block-transactions @db/db addr [txn])
            {:blocks chain}))]
    (handler {:message "submit_transaction" :payload txn} sock-info)
    (is (= 1 (count @db/transaction-pool)))
    (responds {:balance 25 :address (:address wallet/keypair)} {:message "get_balance" :payload (:address wallet/keypair)})
    (is (= b (:payload (handler {:message "submit_block" :payload b} sock-info))))
    (is (= 0 (count @db/transaction-pool)))))

(deftest test-validating-incoming-transactions
  (let [alt-db (atom db/empty-db)]
    (miner/mine-and-commit-db alt-db)
    (let [txn (miner/generate-payment wallet/keypair
                                      (:address wallet/keypair)
                                      25
                                      (q/longest-chain @alt-db))]
      (is (= "transaction-rejected"
             (:message (handler {:message "submit_transaction" :payload txn} sock-info)))))))

(deftest test-blocks-since
  (with-redefs [db/block-chain (atom [])]
    (miner/mine-and-commit)
    (miner/mine-and-commit)
    (miner/mine-and-commit)
    (is (= {:message "blocks_since" :payload (map bc/bhash (drop 1 @db/block-chain)) }
           (handler
                 {:message "get_blocks_since" :payload (bc/bhash (first @db/block-chain))}
                 sock-info)))
    (is (= {:message "blocks_since" :payload []}
           (handler
                 {:message "get_blocks_since" :payload "pizza"}
                 sock-info)))))

(deftest test-validating-incoming-blocks)
