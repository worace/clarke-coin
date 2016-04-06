(ns block-chain.message-handlers-test
  (:require [clojure.test :refer :all]
            [block-chain.utils :refer :all]
            [clojure.math.numeric-tower :as math]
            [clojure.pprint :refer [pprint]]
            [clojure.core.async :as async]
            [block-chain.wallet :as wallet]
            [block-chain.miner :as miner]
            [block-chain.net :as net]
            [block-chain.transactions :as txns]
            [block-chain.chain :as bc]
            [block-chain.db :as db]
            [block-chain.target :as target]
            [block-chain.blocks :as blocks]
            [block-chain.message-handlers :refer :all]))

(defn responds
  ([val msg] (responds val msg {}))
  ([val msg sock-info]
   (let [resp (handler msg sock-info)]
     (is (= val (:payload resp))))))

(def sock-info
  {:remote-address "127.0.0.1"
   :local-port 8334
   :outgoing-port 51283})

(defn test-server
  ([message-fn] (test-server 8335 message-fn))
  ([port message-fn]
   (:server
    (net/start-server
     port
     (fn [req-lines sock-info]
       (let [msg (read-json (first req-lines))]
         (message-fn msg)
         (msg-string {:message-type "pong" :payload (:payload msg)})))))))

(def easy-difficulty (hex-string (math/expt 2 248)))
(def hard-difficulty (hex-string (math/expt 2 50)))

(deftest test-echo
  (let [msg {:message-type "echo"
             :payload "echo this"}]
    (is (= msg (handler msg {})))))

(deftest test-ping-pong
  (let [msg {:message-type "ping"
             :payload (current-time-seconds)}]
    (is (= (assoc msg :message-type "pong") (handler msg {})))))

(deftest test-getting-adding-and-removing-peers
  (with-redefs [db/peers (atom #{})]
    (responds #{} {:message-type "get_peers"})
    (handler {:message-type "add_peer"
              :payload {:port 8335}}
             sock-info)
    (responds #{{:host "127.0.0.1" :port 8335}}
              {:message-type "get_peers"})
    (handler {:message-type "remove_peer"
              :payload {:port 8335}}
             sock-info)
    (responds #{} {:message-type "get_peers"})))

(deftest test-getting-balance-for-key
  (let [chain (atom [])
        key-a (wallet/generate-keypair 512)
        block (blocks/generate-block
               [(miner/coinbase (:address key-a))]
               {:target easy-difficulty})]
    (miner/mine-and-commit chain block)
    (with-redefs [db/block-chain chain]
      (responds {:balance 25 :key (:address key-a)} {:message-type "get_balance" :payload (:address key-a)})
      )))

(deftest test-getting-block-height
  (with-redefs [db/block-chain (atom [])]
    (responds 0 {:message-type "get_block_height"})
    (swap! db/block-chain conj "hi")
    (responds 1 {:message-type "get_block_height"})))

(deftest test-getting-latest-block
  (with-redefs [db/block-chain (atom [])]
    (responds nil {:message-type "get_latest_block"})
    (swap! db/block-chain conj {:some "block"})
    (responds {:some "block"} {:message-type "get_latest_block"})))

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
      (let [utxn (:payload (handler {:message-type "generate_payment"
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
        (handler {:message-type "submit_transaction"
                  :payload payment}
                 sock-info)
        (is (= 1 (count @pool)))
        (miner/mine-and-commit)
        (is (empty? @pool))
        (is (= 0 (bc/balance (:address key-a) @chain)))
        (is (= 25 (bc/balance (:address key-b) @chain)))
        (let [miner-addr (get-in (last @chain) [:transactions 0 :outputs 0 :address])]
          (is (= 25 (bc/balance miner-addr @chain))))
        (is (= 1 (count (:outputs payment))))))))

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
        (handler {:message-type "submit_transaction"
                  :payload payment}
                 sock-info)
        (miner/mine-and-commit)
        (let [miner-addr (get-in (last @chain) [:transactions 0 :outputs 0 :address])]
          ;; miner should have 25 from coinbase and 1 from allotted txn fee
          (is (= 26 (bc/balance miner-addr @chain))))))))

#_(deftest test-forwarding-txns-to-peers
  (let [peer-chan (async/chan)]
    (with-open [peer (:server (net/start-server 8335 (fn [req-lines sock-info]
                                                       (async/go (async/>! peer-chan (read-json (first req-lines)))))))]
      (with-redefs [db/block-chain (atom [])
                    db/transaction-pool (atom #{})
                    db/peers (atom #{})
                    target/default (hex-string (math/expt 2 248))]
        (handler {:message-type "add_peer" :payload {:port 8335}} sock-info)
        (miner/mine-and-commit)
        (let [txn (miner/generate-payment wallet/keypair (:address wallet/keypair) 25 @db/block-chain)]
          (handler {:message-type "submit_transaction" :payload txn} sock-info)
          (let [[message chan] (async/alts!! [peer-chan (async/timeout 500)])]
            (is (= txn (:payload message)))
            (is (= "submit_transaction" (:message-type message))))))
      (.close peer))))

(deftest test-only-forwards-new-transactions
  (let [messages (atom [])]
    (with-open [peer (test-server (fn [m] (swap! messages conj m)))]
      (with-redefs [db/block-chain (atom [])
                    db/transaction-pool (atom #{})
                    db/peers (atom #{})
                    target/default (hex-string (math/expt 2 248))]
        (miner/mine-and-commit)
        (let [txn (miner/generate-payment wallet/keypair (:address wallet/keypair) 25 @db/block-chain)]
          (handler {:message-type "add_peer" :payload {:port 8335}} sock-info)
          ;; send same txn twice but should only get forwarded once
          (handler {:message-type "submit_transaction" :payload txn} sock-info)
          (handler {:message-type "submit_transaction" :payload txn} sock-info)
          (is (= 1 (count @messages)))
          (is (= txn (:payload (first @messages)))))))))

(deftest test-forwarding-mined-blocks-to-peers
  (let [messages (atom [])]
    (with-open [peer (test-server (fn [m] (swap! messages conj m)))]
      (with-redefs [db/block-chain (atom [])
                    db/transaction-pool (atom #{})
                    db/peers (atom #{})
                    target/default (hex-string (math/expt 2 248))]
        (handler {:message-type "add_peer" :payload {:port 8335}} sock-info)
        (miner/mine-and-commit)
        (is (= 1 (count @db/block-chain)))
        (is (= 1 (count @messages)))
        (is (= (first @db/block-chain) (:payload (first @messages))))))))

(deftest test-receiving-new-block-adds-to-block-chain
  (with-redefs [db/block-chain (atom [])
                db/peers (atom #{})]
    (let [b (miner/mine (blocks/generate-block [(miner/coinbase)]
                                               {:blocks []
                                                :target easy-difficulty}))]
      (handler {:message-type "submit_block" :payload b} sock-info)
      (is (= 1 (count @db/block-chain))))))

(deftest test-forwarding-received-blocks-to-peers
  (let [messages (atom [])]
    (with-open [peer (test-server (fn [m] (swap! messages conj m)))]
      (with-redefs [db/block-chain (atom [])
                    db/transaction-pool (atom #{})
                    db/peers (atom #{})
                    target/default (hex-string (math/expt 2 248))]
        (let [b (miner/mine (blocks/generate-block [(miner/coinbase)]
                                                   {:blocks []
                                                    :target easy-difficulty}))]
          (handler {:message-type "add_peer" :payload {:port 8335}} sock-info)
          (handler {:message-type "submit_block" :payload b} sock-info)
          (is (= 1 (count @db/block-chain)))
          (is (= "submit_block" (:message-type (first @messages))))
          (is (= b (:payload (first @messages))))
          (is (= 1 (count @messages))))))))

(deftest test-forwards-received-block-to-peers-only-if-new
  (let [messages (atom [])]
    (with-open [peer (test-server (fn [m] (swap! messages conj m)))]
      (with-redefs [db/block-chain (atom [])
                    db/transaction-pool (atom #{})
                    db/peers (atom #{})]
        (let [b (miner/mine (blocks/generate-block [(miner/coinbase)]
                                                   {:blocks []
                                                    :target easy-difficulty}))]
          (handler {:message-type "add_peer" :payload {:port 8335}} sock-info)
          (handler {:message-type "submit_block" :payload b} sock-info)
          (handler {:message-type "submit_block" :payload b} sock-info)
          (is (= 1 (count @db/block-chain)))
          (is (= "submit_block" (:message-type (first @messages))))
          (is (= b (:payload (first @messages))))
          (is (= 1 (count @messages))))))))


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
      (handler {:message-type "submit_transaction" :payload txn} sock-info)
      (is (= 1 (count @db/transaction-pool)))
      (handler {:message-type "submit_block" :payload b} sock-info)
      (is (= 0 (count @db/transaction-pool))))))

(deftest test-validating-incoming-transactions)

(deftest test-validating-incoming-blocks)

;; Need Validation Logic
;; `validate_transaction`
;; `add_block` - payload: JSON rep of new block - Node should validate

;; Need state / batching logic:
;; `get_blocks`
;; `get_block` - payload: Block Hash of block to get info about - Node

#_(deftest test-transaction-pool
  (with-redefs [db/transaction-pool (atom #{})]
    (let [key (wallet/generate-keypair 512)
          cb (miner/coinbase (:address key))]
      (responds [] {:message-type "get_transaction_pool"})
      (handler {:message-type "submit_transaction" :payload cb} {})
      (responds [cb] {:message-type "get_transaction_pool" :payload cb}))))

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
      (handler {:message-type "submit_block" :payload dummy-block} sock-info)
      (let [[message chan] (async/alts!! [mining-chan (async/timeout 1500)])]
        (is (= "Miner Stopped!" message))
        (is (= 1 (count @db/block-chain)))
        (is (= dummy-block (first @db/block-chain)))))))
