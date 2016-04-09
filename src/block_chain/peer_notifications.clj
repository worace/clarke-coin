(ns block-chain.peer-notifications
  (:require [block-chain.db :as db]
            [block-chain.utils :refer [write-json msg-string]]
            [clojure.core.async
             :as async
             :refer [<! go go-loop chan]]))

(defn block-received!
  [block]
  (doseq [p @db/peers]
    ;; Update to notify over HTTP
    #_(notify-peer (:host p)
                      (:port p)
                      (msg-string
                       {:message "submit_block"
                        :payload block}))))

(defn transaction-received!
  [txn]
  (doseq [p @db/peers]
    ;; Update to notify over HTTP
    #_(notify-peer... (:host p)
                      (:port p)
                      (msg-string
                       {:message "submit_transaction"
                        :payload txn}))))
