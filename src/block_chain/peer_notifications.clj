(ns block-chain.peer-notifications
  (:require [block-chain.db :as db]
            [block-chain.utils :refer [write-json msg-string send-tcp-message]]
            [clojure.core.async
             :as async
             :refer [<! go go-loop chan]]))

(defn block-received!
  [block]
  (doseq [p @db/peers]
    (send-tcp-message (:host p)
                      (:port p)
                      (msg-string
                       {:message-type "submit_block"
                        :payload block}))))

(defn transaction-received!
  [txn]
  (doseq [p @db/peers]
    (send-tcp-message (:host p)
                      (:port p)
                      (msg-string
                       {:message-type "submit_transaction"
                        :payload txn}))))
