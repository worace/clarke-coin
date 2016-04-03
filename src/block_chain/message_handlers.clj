(ns block-chain.message-handlers
  (:require [block-chain.utils :refer :all]
            [block-chain.chain :as bc]
            [block-chain.db :as db]
            [block-chain.wallet :as wallet]
            [block-chain.key-serialization :as ks]
            [block-chain.miner :as miner]
            [block-chain.transactions :as txns]))

(defn echo [msg sock-info]
  msg)

(defn pong [msg sock-info]
  {:message-type "pong" :payload (:payload msg)})

(defn get-peers [msg sock-info]
  {:message-type "peers" :payload @db/peers})

(defn add-peer [msg sock-info]
  (let [host (:remote-address sock-info)
        port (:port (:payload msg))]
    (swap! db/peers conj {:host host :port port})
    {:message-type "peers" :payload @db/peers}))

(defn remove-peer [msg sock-info]
  (let [host (:remote-address sock-info)
        port (:port (:payload msg))]
    (swap! db/peers
           clojure.set/difference
           #{{:host host :port port}})
    {:message-type "peers" :payload @db/peers}))

(defn get-balance [msg sock-info]
  (let [key (:payload msg)
        balance (bc/balance key @db/block-chain)]
    {:message-type "balance"
     :payload {:key key :balance balance}}))

(defn get-transaction-pool [msg sock-info]
  {:message-type "transaction_pool"
   :payload (into [] @db/transaction-pool)})

(defn add-transaction [msg sock-info]
  (swap! db/transaction-pool conj (:payload msg)))

(defn get-block-height [msg sock-info]
  {:message-type "block_height"
   :payload (count @db/block-chain)})

(defn get-latest-block [msg sock-info]
  {:message-type "latest_block"
   :payload (last @db/block-chain)})

(defn get-blocks [msg sock-info]
  {:message-type "blocks"
   :payload @db/block-chain})

(defn get-block [msg sock-info]
  {:message-type "block_info"
   :payload (bc/block-by-hash (:payload msg)
                              @db/block-chain)})

(defn get-transaction [msg sock-info]
  {:message-type "transaction_info"
   :payload (bc/txn-by-hash (:payload msg)
                              @db/block-chain)})

(defn make-payment [msg sock-info]
  ;; what if it fails?
  ;; this should forward the new transaction to other nodes we're connected to
  ;; {:message-type make_payment, :payload {:private-der pizza, :address pizza2, :amount 15}}
  ;; get private der from the message and turn it into a key
  ;; generate new payment transaction for that amount
  ;; use that key to sign that txn
  ;; ...

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  ;;;;;;;;; EXAMPLE ;;;;;;;;;;;;;;;;;;;;;;
  #_(let [sample-private-key ""
        sample-kp (ks/der-string->key-pair sample-private-key)
        recipient-address ""
        amount 15
        txn (miner/generate-payment sample-kp recipient-address amount @db/block-chain 0)]
    (swap! db/transaction-pool conj txn))

  {:message-type "transaction_created"
   :payload "lol"})

(defn generate-payment
  "Generates an _unsigned_ payment transaction for supplied from-address,
   to-address, amount, and fee. Intended for use by wallet-only clients which
   could use this endpoint to generate transactions that they could then sign
   and return to the full node for inclusion in the block chain."
  [msg sock-info]
  {:message-type "unsigned_transaction"
   :payload (miner/generate-unsigned-payment
             (:from-address (:payload msg))
             (:to-address (:payload msg))
             (:amount (:payload msg))
             @db/block-chain
             (or (:fee (:payload msg)) 0))})

(def message-handlers
  {"echo" echo
   "ping" pong
   "get_peers" get-peers
   "add_peer" add-peer
   "remove_peer" remove-peer
   "get_balance" get-balance
   "get_block_height" get-block-height
   "get_latest_block" get-latest-block
   "get_transaction_pool" get-transaction-pool
   "get_blocks" get-blocks
   "get_block" get-block
   "get_transaction" get-transaction
   "add_transaction" add-transaction
   "generate_payment" generate-payment
   "make_payment" make-payment})


(defn handler [msg sock-info]
  (let [handler-fn (get message-handlers
                        (:message-type msg)
                        echo)]
    (handler-fn msg sock-info)))
