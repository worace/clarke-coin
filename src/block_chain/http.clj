(ns block-chain.http
  (:require [compojure.core :refer :all]
            [ring.adapter.jetty :as jetty]
            [block-chain.utils :as utils]
            [ring.util.response :refer [response]]
            [ring.middleware.json :refer [wrap-json-response]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [block-chain.message-handlers :as h]
            [compojure.route :as route]))
;; {"echo" echo X
;;    "ping" ping X
;;    "get_peers" get-peers X
;;    "add_peer" add-peer
;;    "remove_peer" remove-peer
;;    "get_balance" get-balance X
;;    "get_block_height" get-block-height X
;;    "get_latest_block" get-latest-block X
;;    "get_transaction_pool" get-transaction-pool X
;;    "get_blocks" get-blocks X
;;    "get_block" get-block X
;;    "get_transaction" get-transaction X
;;    "submit_transaction" submit-transaction X
;;    "submit_block" submit-block X
;;    "generate_payment" generate-payment}

(defroutes my-routes
  (GET "/" [] "<h1>Hello World</h1>")
  (POST "/echo" req (response (h/handler {:message "echo" :payload (:body req)} {})))
  (POST "/ping" req (response (h/handler {:message "ping" :payload (:body req)} {})))
  (POST "/balance" req (response (h/handler {:message "get_balance" :payload (:address (:body req) )} {})))
  (GET "/blocks" req (response (h/handler {:message "get_blocks" :payload {}} {})))
  (POST "/blocks" req (response (h/handler {:message "submit_block" :payload (:body req)} {})))
  (GET "/peers" req (response (h/handler {:message "get_peers" :payload {}} {})))
  (GET "/pending_transactions" req (response (h/handler {:message "get_transaction_pool" :payload {}} {})))
  (POST "/pending_transactions" req (response (h/handler {:message "submit_transaction" :payload (:body req)} {})))
  (GET "/blocks/:block-hash" [block-hash] (response (h/handler {:message "get_block" :payload block-hash} {})))
  (GET "/latest_block" [] (response (h/handler {:message "get_latest_block" :payload {}} {})))
  (GET "/block_height" [] (response (h/handler {:message "get_block_height" :payload {}} {})))
  (GET "/transactions/:txn-hash" [txn-hash] (response (h/handler {:message "get_transaction" :payload txn-hash} {})))
  (route/not-found "<h1>Page not found</h1>"))

(defn json-body [handler]
  (fn [request]
    (handler (update request :body (comp utils/read-json slurp)))))

(def app (-> my-routes
             (json-body)
             (wrap-json-response)))

(defonce server (atom nil))

(defn start!
  ([] (start! 3000))
  ([port]
   (if-let [server @server]
     (.stop server))
   (let [s (jetty/run-jetty #'app {:port port :join? false})]
     (.start s)
     (reset! server s))))

(defn stop! [] (.stop @server))
