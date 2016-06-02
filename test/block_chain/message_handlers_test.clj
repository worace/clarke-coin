(ns block-chain.message-handlers-test
  (:require [clojure.test :refer :all]
            [block-chain.utils :refer :all]
            [clojure.pprint :refer [pprint]]
            [block-chain.wallet :as wallet]
            [block-chain.target :as target]
            [block-chain.miner :as miner]
            [block-chain.transactions :as txn]
            [org.httpkit.server :as httpkit]
            [block-chain.db :as db]
            [block-chain.queries :as q]
            [block-chain.test-helper :as th]
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

(defn with-peer [f]
  (reset! peer-requests {})
  (let [handler (th/baby-peer peer-requests)
        shutdown-fn (httpkit/run-server handler
                                        {:port test-port})]
    (try
      (f)
      (finally (do (shutdown-fn)
                   (reset! peer-requests {}))))))

(defn with-db [test]
  (with-open [conn (th/temp-db-conn)]
    (reset! db/db (db/db-map conn))
    (miner/mine-and-commit-db! db/db)
    (test)))

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
  (responds {:balance 25 :address (:address wallet/keypair)}
            {:message "get_balance" :payload (:address wallet/keypair)}))

(deftest test-getting-block-height
  (responds 1 {:message "get_block_height"})
  (miner/mine-and-commit-db!)
  (responds 2 {:message "get_block_height"}))

(deftest test-getting-latest-block
  (is (not (nil? (q/highest-block @db/db))))
  (responds (q/highest-block @db/db) {:message "get_latest_block"}))

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
  (let [b (miner/mine (blocks/generate-block [(txn/coinbase @db/db)]
                                             @db/db))]
    (is (= b (:payload (handler {:message "submit_block" :payload b} sock-info))))
    (is (= b (q/highest-block @db/db)))
    (is (= 2 (q/chain-length @db/db)))))

(deftest test-forwarding-received-blocks-to-peers
  (q/add-peer! db/db {:port test-port :host "127.0.0.1"})
  (let [b (miner/mine (blocks/generate-block [(txn/coinbase @db/db)]
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
  (let [b (miner/mine (blocks/generate-block [(txn/coinbase @db/db)]
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
  (let [addr (:address (wallet/generate-keypair 512))
        bad-txn (-> (txn/payment wallet/keypair addr 25 @db/db)
                    (update-in [:outputs 0 :amount] inc))]
      (let [resp (handler {:message "submit_transaction"
                           :payload bad-txn}
                          sock-info)]
        (is (= "transaction-rejected" (:message resp))))))

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

(deftest test-receiving-lower-alt-block-does-not-change-latest-block
  (let [alt-block-1 (miner/mine (miner/next-block @db/db
                                                  (:address (wallet/generate-keypair 512))))]
    (miner/mine-and-commit-db!)
    (miner/mine-and-commit-db!)
    (miner/mine-and-commit-db!)
    (is (= 4 (q/chain-length @db/db)))
    (let [prev-head (q/highest-block @db/db)]
      (is (= {:message "block-accepted" :payload alt-block-1}
             (handler {:message "submit_block" :payload alt-block-1} sock-info)))
      (is (= prev-head (q/highest-block @db/db)))
      (is (= 4 (q/chain-length @db/db))))))

(deftest test-receiving-higher-blocks-moves-the-chain-forward
  (let [peer-db (atom nil)]
    (with-open [peer-conn (th/temp-db-conn)]
      (reset! peer-db (db/db-map peer-conn))
      (swap! peer-db assoc :default-key (wallet/generate-keypair 512))
      ;; Both Dbs start with 1 (shared genesis block)
      (q/add-block! peer-db (q/root-block @db/db))
      ;; DB     - A
      ;; PeerDb - A
      (is (= 1 (q/chain-length @db/db)))
      (is (= 1 (q/chain-length @peer-db)))

      ;; db/db advances by 2
      ;; DB     - A - B - C
      ;; PeerDb - A
      (miner/mine-and-commit-db! db/db)
      (miner/mine-and-commit-db! db/db)

      (is (= 3 (q/chain-length @db/db)))
      (is (= 1 (q/chain-length @peer-db)))

      ;; peer-db advances by 3 -- now ahead
      ;; DB     - A - B - C
      ;; PeerDb - A - D - E - F
      (miner/mine-and-commit-db! peer-db)
      (miner/mine-and-commit-db! peer-db)
      (miner/mine-and-commit-db! peer-db)

      (is (= 4 (q/chain-length @peer-db)))

      (doseq [b (drop 1 (reverse (q/longest-chain @peer-db)))]
        (is (= {:message "block-accepted" :payload b}
               (handler {:message "submit_block" :payload b} sock-info))))
      ;;              B - C - G - H
      ;;             /
      ;; DB     - A - D - E - F
      ;;
      ;; PeerDb - A - D - E - F
      ;;            \
      ;;             B - C - G - H
      (is (= 4 (q/chain-length @db/db)))
      (is (= (q/highest-block @peer-db)
             (q/highest-block @db/db))))))

(defn timed-out? [start duration]
  (> (- (current-time-millis) start) duration))

(deftest test-receiving-outside-block-stops-miner
  (let [alt-block (miner/mine (miner/next-block @db/db))
        pending (-> (miner/next-block @db/db)
                    (assoc-in [:header :target] target/hard))
        mining-future (future
                        (miner/mine pending))]

    (is (= {:message "block-accepted" :payload alt-block}
           (handler {:message "submit_block" :payload alt-block}
                    sock-info)))

    (is (= :finished
           (loop [start-time (current-time-millis)]
             (cond
               (timed-out? start-time 500) :timeout-elapsed
               (future-done? mining-future) :finished
               :else (recur start-time))
             )))
    (miner/interrupt-miner!)))
