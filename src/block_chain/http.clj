(ns block-chain.http
  (:require [compojure.core :refer :all]
            [ring.adapter.jetty :as jetty]
            [block-chain.utils :as utils]
            [compojure.api.sweet :as sweet]
            [ring.util.http-response :refer :all]
            [ring.util.response :refer [response]]
            [ring.middleware.json :refer [wrap-json-response]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [block-chain.message-handlers :as h]
            [schema.core :as s]
            [block-chain.schemas :refer :all]
            [compojure.route :as route]))



(def test-api
  (sweet/api
   {:swagger {:ui "/api-docs"
              :spec "/swagger.json"
              :data {:info {:title "ClarkeCoin Full-Node API"
                            :description "Endpoints and parameters for API."}}}}
   (sweet/GET "/hello" []
              :return {:message String}
              :query-params [name :- String]
              :summary "Say hello"
              (ok {:message (str "Hello, " name)}))

   (sweet/GET "/blocks" []
              :return {:message String :payload [Block]}
              :summary "Fetch all blocks from this node's copy of the chain."
              (let [resp (h/handler {:message "get_blocks" :payload {}} {})]
                (ok resp))
              (ok (h/handler {:message "get_blocks" :payload {}} {})))

   (sweet/GET "/peers" []
              :return {:message String :payload [Peer]}
              :summary "List all of the peers to which this node is currently connected."
              (ok (h/handler {:message "get_peers" :payload {}} {})))

   (sweet/GET "/pending_transactions" []
              :return {:message String :payload [Transaction]}
              :summary "List all transactions this node is aware of which have been submitted and validated but not yet included in a block."
              (ok (h/handler {:message "get_transaction_pool" :payload {}} {})))

   (sweet/GET "/blocks/:block-hash"
              [block-hash]
              :path-params [block-hash :- (sweet/describe s/Str "Hexadecimal string representing SHA256 hash of the desired block")]
              :return {:message String :payload Block}
              :summary "Fetch a specific block by providing its hash."
              (ok (h/handler {:message "get_block" :payload block-hash} {})))

   (sweet/GET "/latest_block" []
        :return {:message String :payload Block}
        :summary "Fetch the latest block in this node's copy of the chain."
        (ok (h/handler {:message "get_latest_block" :payload {}} {})))

   (sweet/GET "/block_height" []
        :return {:message String :payload s/Int}
        :summary "Get the number of blocks on this node's copy of the chain."
        (ok (h/handler {:message "get_block_height" :payload {}} {})))

   (sweet/GET "/transactions/:txn-hash"
              [txn-hash]
              :path-params [block-hash :- (sweet/describe s/Str "Hexadecimal string representing SHA256 hash of the desired transaction")]
              :return {:message String :payload Transaction}
              :summary "Fetch a specific transaction by providing its hash."
              (ok (h/handler {:message "get_transaction" :payload txn-hash} {})))

   (sweet/POST "/echo"
               req
               :return {:message String}
               :body-params [message :- s/Str]
               :summary "Echoes the body of your request back to you"
               (ok (h/handler {:message message} {})))

   (sweet/POST "/ping" req
               :return {:pong s/Int}
               :body-params [ping :- s/Int]
               :summary "Ping with a timestamp and receive a Pong with a corresponding timestamp"
               (ok {:pong (:ping (utils/read-json (slurp (:body req))))}))

   (sweet/POST "/balance" req
               :return {:message String :payload {:address s/Str :balance s/Int}}
               :body-params [address :- s/Str]
               :summary "Get balance for a given ClarkeCoin address (DER-encoded RSA public key)."
               (ok (h/handler {:message "get_balance" :payload (:address (utils/read-json (slurp (:body req))) )} {})))

   (sweet/POST "/blocks" req
               :return {:message String :payload Block}
               :body [block Block]
               :summary "Submit a new block to this node for inclusion in the chain."
               (ok (h/handler {:message "submit_block" :payload block} {})))

   (sweet/POST "/pending_transactions" req
               :return {:message String :payload Transaction}
               :body [transaction Transaction]
               :summary "Submit a new transaction to this node for inclusion in the next block."
               (ok (h/handler {:message "submit_transaction" :payload transaction} {})))
   (route/not-found {:status 404 :body {:error "not found"}})
   ))

(defn json-body [handler]
  (fn [request]
    (handler (update request :body (comp utils/read-json slurp)))))

(defonce server (atom nil))

(defn stop! [] (if-let [server @server] (.stop server)))
(defn start!
  ([] (start! 3001))
  ([port]
   (stop!)
   (let [s (jetty/run-jetty #'test-api {:port port :join? false})]
     (.start s)
     (reset! server s))))

(start!)

#_(defroutes my-routes
  (GET "/blocks" req (response (h/handler {:message "get_blocks" :payload {}} {})))
  (GET "/peers" req (response (h/handler {:message "get_peers" :payload {}} {})))
  (GET "/pending_transactions" req (response (h/handler {:message "get_transaction_pool" :payload {}} {})))
  (GET "/blocks/:block-hash" [block-hash] (response (h/handler {:message "get_block" :payload block-hash} {})))
  (GET "/latest_block" [] (response (h/handler {:message "get_latest_block" :payload {}} {})))
  (GET "/block_height" [] (response (h/handler {:message "get_block_height" :payload {}} {})))
  (GET "/transactions/:txn-hash" [txn-hash] (response (h/handler {:message "get_transaction" :payload txn-hash} {})))
  (POST "/echo" req (response (h/handler {:message "echo" :payload (:body req)} {})))
  (POST "/ping" req (response (h/handler {:message "ping" :payload (:body req)} {})))
  (POST "/balance" req (response (h/handler {:message "get_balance" :payload (:address (:body req) )} {})))
  (POST "/blocks" req (response (h/handler {:message "submit_block" :payload (:body req)} {})))
  (POST "/pending_transactions" req (response (h/handler {:message "submit_transaction" :payload (:body req)} {})))
  (route/not-found "<h1>Page not found</h1>"))
#_(def app (-> my-routes
             (json-body)
             (wrap-json-response)))

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
