(ns block-chain.message-handlers
  (:require [block-chain.utils :refer :all]
            [block-chain.chain :as bc]
            [clojure.pprint :refer [pprint]]
            [block-chain.db :as db]
            [block-chain.queries :as q]
            [block-chain.transaction-validations :as txn-v]
            [block-chain.block-validations :as block-v]
            [block-chain.block-sync :as block-sync]
            [block-chain.wallet :as wallet]
            [block-chain.key-serialization :as ks]
            [block-chain.miner :as miner]
            [block-chain.peer-notifications :as peers]
            [block-chain.transactions :as txns]))

(defn echo [msg sock-info] msg)

(defn ping [msg sock-info] {:message "pong" :payload (:payload msg)})

(defn get-peers [msg sock-info]
  {:message "peers" :payload (q/peers @db/db)})

(defn add-peer [msg sock-info]
  (let [host (:remote-address sock-info)
        port (:port (:payload msg))]
    (swap! db/db q/add-peer {:host host :port port})
    (block-sync/sync-if-needed! db/block-chain {:host host :port port})
    {:message "peers" :payload @db/peers}))

(defn remove-peer [msg sock-info]
  (let [host (:remote-address sock-info)
        port (:port (:payload msg))]
    (swap! db/db q/remove-peer {:host host :port port})
    {:message "peers" :payload (q/peers @db/db)}))

(defn get-balance [msg sock-info]
  (let [address (:payload msg)
        balance (bc/balance address (q/longest-chain @db/db))]
    {:message "balance"
     :payload {:address address :balance balance}}))

(defn get-transaction-pool [msg sock-info]
  {:message "transaction_pool"
   :payload (into [] @db/transaction-pool)})

(defn get-block-height [msg sock-info]
  {:message "block_height"
   :payload (q/chain-length @db/db)})

(defn get-latest-block [msg sock-info]
  {:message "latest_block"
   :payload (last @db/block-chain)})

(defn get-blocks [msg sock-info]
  {:message "blocks"
   :payload (into [] (reverse (q/longest-chain @db/db)))})

(defn get-block [msg sock-info]
  {:message "block_info"
   :payload (bc/block-by-hash (:payload msg)
                              @db/block-chain)})

(defn get-transaction [msg sock-info]
  {:message "transaction_info"
   :payload (bc/txn-by-hash (:payload msg)
                              @db/block-chain)})

(defn generate-payment
  "Generates an _unsigned_ payment transaction for supplied from-address,
   to-address, amount, and fee. Intended for use by wallet-only clients which
   could use this endpoint to generate transactions that they could then sign
   and return to the full node for inclusion in the block chain."
  [msg sock-info]
  {:message "unsigned_transaction"
   :payload (miner/generate-unsigned-payment
             (:from-address (:payload msg))
             (:to-address (:payload msg))
             (:amount (:payload msg))
             @db/block-chain
             (or (:fee (:payload msg)) 0))})

(defn submit-transaction [msg sock-info]
  (let [txn (:payload msg)
        validation-errors (txn-v/validate-transaction txn (q/longest-chain @db/db) @db/transaction-pool)]
    (if (empty? validation-errors)
      (do
        (swap! db/transaction-pool conj txn)
        (peers/transaction-received! txn)
        {:message "transaction-accepted" :payload txn})
      {:message "transaction-rejected" :payload validation-errors})))

(defn submit-block [msg sock-info]
  (let [b (:payload msg)
        validation-errors (block-v/validate-block b (q/longest-chain @db/db))]
    (if (empty? validation-errors)
      (do
        (miner/stop-miner!)
        (swap! db/db q/add-block b)
        (reset! db/transaction-pool #{})
        (peers/block-received! b)
        {:message "block-accepted" :payload b})
      {:message "block-rejected" :payload validation-errors})))

(defn get-blocks-since [msg sock-info]
  (let [start-pos (bc/block-index (:payload msg) @db/block-chain)]
    (if start-pos
      {:message "blocks_since" :payload (map bc/bhash (drop (inc start-pos) @db/block-chain))}
      {:message "blocks_since" :payload []})))

(def message-handlers
  {"echo" echo
   "ping" ping
   "get_peers" get-peers
   "add_peer" add-peer
   "remove_peer" remove-peer
   "get_balance" get-balance
   "get_block_height" get-block-height
   "get_latest_block" get-latest-block
   "get_transaction_pool" get-transaction-pool
   "get_blocks" get-blocks
   "get_block" get-block
   "get_blocks_since" get-blocks-since
   "get_transaction" get-transaction
   "submit_transaction" submit-transaction
   "submit_block" submit-block
   "generate_payment" generate-payment})


(defn handler [msg sock-info]
  ;; (println "*****\n\n" "Message handler called with: " msg)
  (let [handler-fn (get message-handlers
                        (:message msg)
                        echo)
        resp (handler-fn msg sock-info)]
    ;; (println "######\n\n" "Message handler returning value: " resp)
    resp))
