(ns block-chain.message-handlers-test
  (:require [clojure.test :refer :all]
            [block-chain.utils :refer :all]
            [block-chain.peers :as peers]
            [clojure.math.numeric-tower :as math]
            [block-chain.wallet :as wallet]
            [block-chain.miner :as miner]
            [block-chain.transactions :as txns]
            [block-chain.chain :as bc]
            [block-chain.blocks :as blocks]
            [block-chain.message-handlers :refer :all]))

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
  (peers/reset-peers!)
  (is (= [] (:payload (handler {:message-type "get_peers"} {}))))
  (handler {:message-type "add_peer"
            :payload {:port 8335}}
           sock-info)
  (is (= [{:host "127.0.0.1" :port 8335}]
         (:payload (handler {:message-type "get_peers"} {}))))
  (handler {:message-type "remove_peer"
            :payload {:port 8335}}
           sock-info)
  (is (= []
         (:payload (handler {:message-type "get_peers"} {})))))

(deftest test-getting-balance-for-key
  (let [key-a (wallet/generate-keypair 512)
        easy-diff (hex-string (math/expt 2 248))
        block (blocks/generate-block
               [(miner/coinbase (:public-pem key-a))]
               {:target easy-diff})]
    (miner/mine-and-commit bc/block-chain
                           block)
    (-> {:message-type "get_balance" :payload (:public-pem key-a)}
        (handler {})
        :payload
        :balance
        (= 25)
        is)))

(deftest test-transaction-pool
  (txns/clear-pool!)
  (-> {:message-type "get_transaction_pool"}
      (handler {})
      :payload
      (= [])
      is)
  (let [key (wallet/generate-keypair 512)
        cb (miner/coinbase (:public-pem key))]
    (handler {:message-type "add_transaction" :payload cb} {})
    (-> {:message-type "get_transaction_pool"}
        (handler {})
        :payload
        (= [cb])
        is)))

;; `validate_transaction`

;; __Blocks__

;; `get_blocks`
;; `get_block` - payload: Block Hash of block to get info about - Node
;; `add_block` - payload: JSON rep of new block - Node should validate
;; `get_block_height` - payload: none - Node should respond with
;; `get_latest_block` - payload: none - Node should respond with hash of the

;; __Misc__

;; * Chat - `chat` - payload: message to send - Just for funsies - sned an arbitrary message
;; to one of your peers. No response required
;; * Echo - `echo` - payload: arbitrary - Node should respond with the same payload that
;; was sent
;; `ping`
