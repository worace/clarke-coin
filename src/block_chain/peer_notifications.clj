(ns block-chain.peer-notifications
  (:require [block-chain.db :as db]
            [block-chain.queries :as q]
            [block-chain.log :as log]
            [block-chain.peer-client :as pc]
            [clj-http.client :as http]))

(defn block-received!
  [block]
  (doseq [p (q/peers @db/db)]
    (log/info "Notifying peer" p "of block:" (q/bhash block))
    (pc/send-block p block)))

(defn transaction-received!
  [txn]
  (doseq [p (q/peers @db/db)]
    (log/info "Notifying peer" p "of txn:" (:hash txn))
    (pc/send-txn p txn)))
