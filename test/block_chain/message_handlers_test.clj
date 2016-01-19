(ns block-chain.message-handlers-test
  (:require [clojure.test :refer :all]
            [block-chain.utils :refer :all]
            [clojure.math.numeric-tower :as math]
            [block-chain.wallet :as wallet]
            [block-chain.miner :as miner]
            [block-chain.transactions :as txns]
            [block-chain.chain :as bc]
            [block-chain.db :as db]
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
        easy-diff (hex-string (math/expt 2 248))
        block (blocks/generate-block
               [(miner/coinbase (:public-pem key-a))]
               {:target easy-diff})]
    (miner/mine-and-commit chain block)
    (with-redefs [db/block-chain chain]
      (responds {:balance 25 :key (:public-pem key-a)} {:message-type "get_balance" :payload (:public-pem key-a)})
      )))

(deftest test-transaction-pool
  (with-redefs [db/transaction-pool (atom #{})]
    (let [key (wallet/generate-keypair 512)
          cb (miner/coinbase (:public-pem key))]
      (responds [] {:message-type "get_transaction_pool"})
      (handler {:message-type "add_transaction" :payload cb} {})
      (responds [cb] {:message-type "get_transaction_pool" :payload cb}))))

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

;; Need Validation Logic
;; `validate_transaction`
;; `add_block` - payload: JSON rep of new block - Node should validate

;; Need state / batching logic:
;; `get_blocks`
;; `get_block` - payload: Block Hash of block to get info about - Node
