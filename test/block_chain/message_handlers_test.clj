(ns block-chain.message-handlers-test
  (:require [clojure.test :refer :all]
            [block-chain.utils :refer :all]
            [clojure.math.numeric-tower :as math]
            [clojure.pprint :refer [pprint]]
            [clojure.core.async :as async]
            [block-chain.wallet :as wallet]
            [block-chain.miner :as miner]
            [block-chain.transactions :as txns]
            [block-chain.chain :as bc]
            [ring.adapter.jetty :as jetty]
            [compojure.core :refer [routes]]
            [compojure.route :as route]
            [block-chain.db :as db]
            [block-chain.target :as target]
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

(def easy-difficulty (hex-string (math/expt 2 248)))
(def hard-difficulty (hex-string (math/expt 2 50)))

(deftest test-echo
  (let [msg {:message "echo"
             :payload "echo this"}]
    (is (= msg (handler msg {})))))

(deftest test-ping-pong
  (let [msg {:message "ping"
             :payload (current-time-seconds)}]
    (is (= (assoc msg :message "pong") (handler msg {})))))

(deftest test-getting-adding-and-removing-peers
  (with-redefs [db/peers (atom #{})]
    (responds [] {:message "get_peers"})
    (handler {:message "add_peer"
              :payload {:port 8335}}
             sock-info)
    (responds [{:host "127.0.0.1" :port 8335}]
              {:message "get_peers"})
    (handler {:message "remove_peer"
              :payload {:port 8335}}
             sock-info)
    (responds [] {:message "get_peers"})))

(deftest test-getting-balance-for-key
  (let [chain (atom [])
        key-a (wallet/generate-keypair 512)
        block (blocks/generate-block
               [(miner/coinbase (:address key-a))]
               {:target easy-difficulty})]
    (miner/mine-and-commit chain block)
    (with-redefs [db/block-chain chain]
      (responds {:balance 25 :address (:address key-a)} {:message "get_balance" :payload (:address key-a)})
      )))

(deftest test-getting-block-height
  (with-redefs [db/block-chain (atom [])]
    (responds 0 {:message "get_block_height"})
    (swap! db/block-chain conj "hi")
    (responds 1 {:message "get_block_height"})))

(deftest test-getting-latest-block
  (with-redefs [db/block-chain (atom [])]
    (responds nil {:message "get_latest_block"})
    (swap! db/block-chain conj {:some "block"})
    (responds {:some "block"} {:message "get_latest_block"})))

(deftest test-generating-transaction
  (let [chain (atom [])
        key-a (wallet/generate-keypair 512)
        key-b (wallet/generate-keypair 512)
        easy-difficulty (hex-string (math/expt 2 248))
        block (blocks/generate-block
               [(miner/coinbase (:address key-a))]
               {:target easy-difficulty})]
    (miner/mine-and-commit chain block)
    (with-redefs [db/block-chain chain]
      (let [utxn (:payload (handler {:message "generate_payment"
                                     :payload {:amount 15
                                               :from-address (:address key-a)
                                               :to-address (:address key-b)
                                               :fee 3}}
                                    sock-info))]
        (is (= 1 (count (:inputs utxn))))
        ;; verify diff b/t inputs and outputs accounts for fee?
        ;; (is (= 3 (- (:amount (first (:inputs utxn)))
        ;;             (reduce + (map :amount (:outputs utxn))))))
        (is (nil? (get-in utxn [:inputs 0 :signature])))
        (is (= 2 (count (:outputs utxn))))))))

(deftest test-submitting-transaction
  (let [chain (atom [])
        pool (atom #{})
        key-a (wallet/generate-keypair 512)
        key-b (wallet/generate-keypair 512)
        easy-difficulty (hex-string (math/expt 2 248))
        block (blocks/generate-block
               [(miner/coinbase (:address key-a))]
               {:target easy-difficulty})]
    (miner/mine-and-commit chain block)
    (with-redefs [db/block-chain chain
                  db/transaction-pool pool
                  target/default (hex-string (math/expt 2 248) )]
      (let [payment (miner/generate-payment key-a (:address key-b) 25 @chain)]
        (is (= payment (s/validate Transaction payment)))
        (is (= 25 (bc/balance (:address key-a) @chain)))
        ;; (is (= {:message "transaction-accepted"
        ;;         :payload payment}
        ;;        (handler {:message "submit_transaction"
        ;;                  :payload payment}
        ;;                 sock-info)))
        ;; (is (= 1 (count @pool)))
        ;; (miner/mine-and-commit)
        ;; (is (empty? @pool))
        ;; (is (= 0 (bc/balance (:address key-a) @chain)))
        ;; (is (= 25 (bc/balance (:address key-b) @chain)))
        ;; (let [miner-addr (get-in (last @chain) [:transactions 0 :outputs 0 :address])]
        ;;   (is (= 25 (bc/balance miner-addr @chain))))
        ;; (is (= 1 (count (:outputs payment))))
        ))))

(deftest test-submitting-transaction-with-txn-fee
  (let [chain (atom [])
        pool (atom #{})
        key-a (wallet/generate-keypair 512)
        key-b (wallet/generate-keypair 512)
        block (blocks/generate-block
               [(miner/coinbase (:address key-a))]
               {:target easy-difficulty})]
    (miner/mine-and-commit chain block)
    (with-redefs [db/block-chain chain
                  db/transaction-pool pool
                  target/default (hex-string (math/expt 2 248) )]
      (let [payment (miner/generate-payment key-a (:address key-b) 24 @chain 1)]
        (handler {:message "submit_transaction"
                  :payload payment}
                 sock-info)
        (miner/mine-and-commit)
        (let [miner-addr (get-in (last @chain) [:transactions 0 :outputs 0 :address])]
          ;; miner should have 25 from coinbase and 1 from allotted txn fee
          (is (= 26 (bc/balance miner-addr @chain))))))))

(deftest test-only-forwards-new-transactions
  (let [reqs (atom [])]
    (with-test-handler [peer (fn [req] (swap! reqs conj req))]
      (with-redefs [db/block-chain (atom [])
                    db/transaction-pool (atom #{})
                    db/peers (atom #{})
                    target/default (hex-string (math/expt 2 248))]
        (miner/mine-and-commit)
        (is (= 0 (count @db/transaction-pool)))
        (let [txn (miner/generate-payment wallet/keypair (:address wallet/keypair) 25 @db/block-chain)]
          (handler {:message "add_peer" :payload {:port test-port}} sock-info)
          ;; send same txn twice but should only get forwarded once
          (is (= {:message "transaction-accepted" :payload txn} (handler {:message "submit_transaction" :payload txn} sock-info)))
          (handler {:message "submit_transaction" :payload txn} sock-info)
          (is (= 1 (count @reqs)))
          (is (= "/pending_transactions" (:uri (first @reqs))))
          (is (= :post (:request-method (first @reqs))))
          (is (= txn (read-json (:body (first @reqs))))))))))

(defn json-body [req] (read-json (:body req)))
(deftest test-forwarding-mined-blocks-to-peers
  (let [reqs (atom [])]
    (with-test-handler [peer (fn [req] (swap! reqs conj req))]
      (with-redefs [db/block-chain (atom [])
                    db/transaction-pool (atom #{})
                    db/peers (atom #{})
                    target/default (hex-string (math/expt 2 248))]
        (handler {:message "add_peer" :payload {:port test-port}} sock-info)
        (miner/mine-and-commit)
        (is (= 1 (count @db/block-chain)))
        (is (= 1 (count @reqs)))
        (is (= "/blocks" (:uri (first @reqs))))
        (is (= :post (:request-method (first @reqs))))
        (is (= (first @db/block-chain)
               (json-body (first @reqs))))))))

(deftest test-receiving-new-block-adds-to-block-chain
  (with-redefs [db/block-chain (atom [])
                db/peers (atom #{})]
    (let [b (miner/mine (blocks/generate-block [(miner/coinbase)]
                                               {:blocks []
                                                :target easy-difficulty}))]
      (handler {:message "submit_block" :payload b} sock-info)
      (is (= 1 (count @db/block-chain))))))

(deftest test-forwarding-received-blocks-to-peers
  (let [reqs (atom [])]
    (with-test-handler [peer (fn [req] (swap! reqs conj req))]
      (with-redefs [db/block-chain (atom [])
                    db/transaction-pool (atom #{})
                    db/peers (atom #{})
                    target/default (hex-string (math/expt 2 248))]
        (let [b (miner/mine (blocks/generate-block [(miner/coinbase)]
                                                   {:blocks []
                                                    :target easy-difficulty}))]
          (handler {:message "add_peer" :payload {:port test-port}} sock-info)
          (handler {:message "submit_block" :payload b} sock-info)
          (is (= 1 (count @db/block-chain)))
          (is (= "/blocks" (:uri (first @reqs))))
          (is (= :post (:request-method (first @reqs))))
          (is (= b (json-body (first @reqs))))
          (is (= 1 (count @reqs))))))))

(deftest test-forwards-received-block-to-peers-only-if-new
  (let [reqs (atom [])]
    (with-test-handler [peer (fn [req] (swap! reqs conj req))]
      (with-redefs [db/block-chain (atom [])
                    db/transaction-pool (atom #{})
                    db/peers (atom #{})]
        (let [b (miner/mine (blocks/generate-block [(miner/coinbase)]
                                                   {:blocks []
                                                    :target easy-difficulty}))]
          (handler {:message "add_peer" :payload {:port test-port}} sock-info)
          (handler {:message "submit_block" :payload b} sock-info)
          (handler {:message "submit_block" :payload b} sock-info)
          (is (= 1 (count @db/block-chain)))
          (is (= "/blocks" (:uri (first @reqs))))
          (is (= :post (:request-method (first @reqs))))
          (is (= b (json-body (first @reqs))))
          (is (= 1 (count @reqs))))))))


(deftest test-receiving-block-clears-txn-pool
  (with-redefs [db/block-chain (atom [])
                db/transaction-pool (atom #{})
                db/peers (atom #{})
                target/default easy-difficulty]
    (miner/mine-and-commit)
    (let [txn (miner/generate-payment wallet/keypair (:address wallet/keypair) 25 @db/block-chain)
          b (miner/mine (blocks/generate-block (into [(miner/coinbase txn)] txn)
                                               {:blocks @db/block-chain
                                                :target easy-difficulty}))]
      (handler {:message "submit_transaction" :payload txn} sock-info)
      (is (= 1 (count @db/transaction-pool)))
      (handler {:message "submit_block" :payload b} sock-info)
      (is (= 0 (count @db/transaction-pool))))))

(deftest test-validating-incoming-transactions
  (with-redefs [db/block-chain (atom [])
                db/transaction-pool (atom #{})
                target/default easy-difficulty]
    (let [alt-chain (atom [])]
      (miner/mine-and-commit alt-chain)
      (let [txn (miner/generate-payment wallet/keypair (:address wallet/keypair) 25 @alt-chain)]
        (is (= "transaction-rejected"
               (:message (handler {:message "submit_transaction" :payload txn} sock-info))))))))

(deftest test-validating-incoming-blocks)

;; Need Validation Logic
;; `validate_transaction`
;; `add_block` - payload: JSON rep of new block - Node should validate

;; Need state / batching logic:
;; `get_blocks`
;; `get_block` - payload: Block Hash of block to get info about - Node

;; Think i have this implemented but still struggling to figure out the best way
;; to test it:
#_(deftest test-receiving-block-stops-miner
  (with-redefs [db/block-chain (atom [])
                db/transaction-pool (atom #{})
                target/default hard-difficulty
                db/peers (atom #{})]
    (let [dummy-block {}
          mining-chan (async/go
                        (println "miner started")
                        (miner/mine-and-commit)
                        (println "miner finished")
                        "Miner Stopped!")]
      (handler {:message "submit_block" :payload dummy-block} sock-info)
      (let [[message chan] (async/alts!! [mining-chan (async/timeout 1500)])]
        (is (= "Miner Stopped!" message))
        (is (= 1 (count @db/block-chain)))
        (is (= dummy-block (first @db/block-chain)))))))
