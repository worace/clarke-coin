(ns block-chain.message-handlers
  (:require [block-chain.utils :refer :all]
            [clojure.pprint :refer [pprint]]
            [block-chain.db :as db]
            [block-chain.queries :as q]
            [block-chain.log :as log]
            [block-chain.blocks :as blocks]
            [block-chain.transaction-validations :as txn-v]
            [block-chain.block-validations :as block-v]
            [block-chain.block-sync :as block-sync]
            [block-chain.wallet :as wallet]
            [block-chain.key-serialization :as ks]
            [block-chain.miner :as miner]
            [block-chain.peer-notifications :as peers]
            [block-chain.peer-client :as pc]
            [block-chain.transactions :as txn]))

(defn echo [msg sock-info] msg)

(defn ping [msg sock-info] {:message "pong" :payload (:payload msg)})

(defn get-peers [msg sock-info]
  {:message "peers" :payload (q/peers @db/db)})

(defn add-peer [msg sock-info]
  (let [host (:remote-address sock-info)
        port (:port (:payload msg))
        peer {:host host :port port}]
    (if (pc/available-peer? peer)
      (do (q/add-peer! db/db peer)
          ;; TODO - need to async this
          (block-sync/sync-if-needed! db/db peer)
          {:message "peers" :payload (q/peers @db/db)})
      {:message "peers" :payload (q/peers @db/db)})))

(defn remove-peer [msg sock-info]
  (let [host (:remote-address sock-info)
        port (:port (:payload msg))]
    (q/remove-peer! db/db {:host host :port port})
    {:message "peers" :payload (q/peers @db/db)}))

(defn get-balance [msg sock-info]
  (let [address (:payload msg)
        balance (q/balance address @db/db)]
    {:message "balance"
     :payload {:address address :balance balance}}))

(defn get-transaction-pool [msg sock-info]
  {:message "transaction_pool"
   :payload (into [] (q/transaction-pool @db/db))})

(defn get-block-height [msg sock-info]
  {:message "block_height"
   :payload (q/chain-length @db/db)})

(defn get-latest-block [msg sock-info]
  {:message "latest_block"
   :payload (q/highest-block @db/db)})

(defn get-blocks [msg sock-info]
  {:message "blocks"
   :payload (into [] (reverse (q/longest-chain @db/db)))})

(defn get-block [msg sock-info]
  {:message "block_info"
   :payload (q/get-block @db/db
                         (:payload msg))})

(defn get-transaction [msg sock-info]
  {:message "transaction_info"
   :payload (q/get-txn @db/db (:payload msg))})

(defn generate-payment
  "Generates an _unsigned_ payment transaction for supplied from-address,
   to-address, amount, and fee. Intended for use by wallet-only clients which
   could use this endpoint to generate transactions that they could then sign
   and return to the full node for inclusion in the block chain."
  [msg sock-info]
  (let [{{:keys [from-address to-address amount fee]} :payload} msg
        fee (or fee 0)
        total (+ amount fee)
        balance (q/balance from-address @db/db)]
    (if (>= balance total)
      {:message "unsigned_transaction"
       :payload (txn/unsigned-payment
                 from-address
                 to-address
                 amount
                 @db/db
                 fee)}
      {:message "insufficient_balance"
       :payload (str "Found " balance " to fund " total)})))

(defn submit-transaction [msg sock-info]
  (let [txn (:payload msg)
        validation-errors (txn-v/validate-transaction @db/db
                                                      txn)]
    (cond
      (not (txn-v/new-transaction? @db/db txn)) {:message "transaction-rejected" :payload ["Transaction already in this node's pool."]}
      (not (txn-v/inputs-unspent-in-txn-pool? @db/db txn)) {:message "transaction-rejected" :payload ["One or more of Transaction's inputs have already been spent by a pending transaction."]}
      (not (empty? validation-errors)) {:message "transaction-rejected" :payload validation-errors}
      :else (do
              (q/add-transaction-to-pool! db/db txn)
              (miner/interrupt-miner!)
              (peers/transaction-received! txn)
              {:message "transaction-accepted" :payload txn}))))

(defn submit-block [msg sock-info]
  (let [b (:payload msg)
        validation-errors (block-v/validate-block @db/db b)]
    (if (empty? validation-errors)
      (if (q/new-block? @db/db b)
        (do
          (miner/interrupt-miner!)
          (swap! db/db q/add-block b)
          (peers/block-received! b)
          (log/info "Received block" (q/bhash b))
          (log/info "Current head" (q/bhash (q/highest-block @db/db)))
          {:message "block-accepted" :payload b})
        {:message "block-rejected" :payload ["Block already known."]})
      {:message "block-rejected" :payload validation-errors})))

(defn get-blocks-since [msg sock-info]
  (if-let [b (q/get-block @db/db (:payload msg))]
    {:message "blocks_since"
     :payload (map q/bhash (q/blocks-since @db/db (:payload msg)))}
    {:message "blocks_since" :payload []}))

(defn generate-unmined-block [msg sock-info]
  (let [addr (or (:payload msg) (q/wallet-addr @db/db))]
    {:message "unmined_block"
     :payload (blocks/hashed (miner/next-block @db/db addr))}))

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
   "generate_unmined_block" generate-unmined-block
   "generate_payment" generate-payment})


(defn handler [msg sock-info]
  (let [handler-fn (get message-handlers
                        (:message msg)
                        echo)
        resp (handler-fn msg sock-info)]
    resp))
