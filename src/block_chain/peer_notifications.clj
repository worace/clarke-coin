(ns block-chain.peer-notifications
  (:require [block-chain.db :as db]
            [block-chain.utils :refer [write-json msg-string send-tcp-message]]
            [clojure.core.async
             :as async
             :refer [<! go go-loop chan]]))

(defn notify! [event-type payload]
  )


(defonce notifs-chan (chan))

(defonce running? (atom true))

(defn stop-notifier! [] (reset! running? false))

(defn start-notifier! []
  (reset! running? true)
  (go-loop []
    (let [event (<! notifs-chan)]
      (println "GOT EVENT: " event))
    (if @running? (recur))))


(defn transaction-received!
  [txn]
  (doseq [p @db/peers]
    (println "will contact p: " p)
    (go
      (send-tcp-message (:host p)
                        (:port p)
                        (msg-string
                         {:message-type "submit_transaction"
                          :payload txn})))))
