(ns block-chain.peer-notifications
  (:require [block-chain.db :as db]
            [block-chain.queries :as q]
            [block-chain.peer-client :as pc]
            [clj-http.client :as http]))

(defn block-received!
  [block]
  (doseq [p (q/peers @db/db)]
    (println "Notifying peer of block: " p)
    (pc/send-block p block)))

(defn transaction-received!
  [txn]
  (doseq [p (q/peers @db/db)]
    (println "Notifying peer of txn: " p)
    (pc/send-txn p txn)))
