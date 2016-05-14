(ns block-chain.peer-notifications
  (:require [block-chain.db :as db]
            [block-chain.queries :as q]
            [clj-http.client :as http]
            [clojure.core.async
             :as async
             :refer [<! go go-loop chan]]))

(defn block-received!
  [block]
  (doseq [p (q/peers @db/db)]
    (println "Notifying peer of block: " p)
    (http/post (str "http://" (:host p) ":" (:port p) "/blocks")
                 {:content-type :json
                  :form-params block})))

(defn transaction-received!
  [txn]
  (doseq [p (q/peers @db/db)]
    (println "Notifying peer of txn: " p)
    (http/post (str "http://" (:host p) ":" (:port p) "/pending_transactions")
               {:content-type :json
                :form-params txn})))
