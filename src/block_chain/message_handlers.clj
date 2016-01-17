(ns block-chain.message-handlers
  (:require [block-chain.utils :refer :all]
            [block-chain.chain :as bc]
            [block-chain.transactions :as txns]
            [block-chain.peers :as peers]))

(defn echo [msg sock-info]
  msg)

(defn pong [msg sock-info]
  {:message-type "pong" :payload (:payload msg)})

(defn get-peers [msg sock-info]
  {:message-type "peers" :payload (peers/get-peers)})

(defn add-peer [msg sock-info]
  (let [host (:remote-address sock-info)
        port (:port (:payload msg))]
    (peers/add-peer! {:host host :port port})
    {:message-type "peers" :payload (peers/get-peers)}))

(defn remove-peer [msg sock-info]
  (let [host (:remote-address sock-info)
        port (:port (:payload msg))]
    (peers/remove-peer! {:host host :port port})
    {:message-type "peers" :payload (peers/get-peers)}))

(defn get-balance [msg sock-info]
  (let [key (:payload msg)
        balance (bc/balance key @bc/block-chain)]
    {:message-type "balance"
     :payload {:key key :balance balance}}))

(defn get-transaction-pool [msg sock-info]
  {:message-type "transaction_pool"
   :payload (txns/pool)})

(defn add-transaction [msg sock-info]
  (txns/add! (:payload msg)))

(def message-handlers
  {"echo" echo
   "ping" pong
   "get_peers" get-peers
   "add_peer" add-peer
   "remove_peer" remove-peer
   "get_balance" get-balance
   "get_transaction_pool" get-transaction-pool
   "add_transaction" add-transaction})

(defn handler [msg sock-info]
  (let [handler-fn (get message-handlers
                        (:message-type msg)
                        echo)]
    (handler-fn msg sock-info)))
