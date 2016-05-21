(ns block-chain.http
  (:require [org.httpkit.server :as httpkit]
            [compojure.api.sweet :as sweet]
            [ring.util.http-response :refer :all]
            [block-chain.message-handlers :as h]
            [clojure.pprint :refer [pprint]]
            [block-chain.log :as log]
            [block-chain.utils :refer :all]
            [block-chain.db :as db]
            [block-chain.queries :as q]
            [schema.core :as s]
            [block-chain.schemas :refer :all]
            [clojure.java.io :as io]
            [compojure.core :refer [defroutes GET routes]]
            [compojure.route :as route]))

(defn children-tree
  ([db] (children-tree db (-> db q/longest-chain last q/bhash)))
  ([db hash]
    {:hash hash :label (apply str (take-last 7 hash)) :children (map (partial children-tree db) (q/children db hash))}))

(defroutes web-ui
  (GET "/graph" [] (slurp (io/resource "graph.html")))
  (GET "/tree" [] {:status 200
                   :headers {"Content-Type" "application/json"}
                   :body (write-json (children-tree @db/db))}))

(def api
  (sweet/api
   {:swagger {:ui "/"
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

   (sweet/POST "/peers" request
              :return {:message String :payload [Peer]}
              :body-params [port :- s/Int]
              :summary "Add a peer based on port they provided and their remote addr"
              (ok (h/handler {:message "add_peer" :payload {:port (str port)}}
                             {:remote-address (:remote-addr request)})))

   (sweet/GET "/pending_transactions" []
              :return {:message String :payload [Transaction]}
              :summary "List all transactions this node is aware of which have been submitted and validated but not yet included in a block."
              (ok (h/handler {:message "get_transaction_pool" :payload {}} {})))

   (sweet/GET "/blocks/:block-hash"
              [block-hash]
              :path-params [block-hash :- (sweet/describe s/Str "hexadecimal string representing sha256 hash of the desired block")]
              :return {:message String :payload Block}
              :summary "fetch a specific block by providing its hash."
              (ok (h/handler {:message "get_block" :payload block-hash} {})))

   (sweet/GET "/blocks_since/:block-hash"
              [block-hash]
              :path-params [block-hash :- (sweet/describe s/Str "hexadecimal string representing sha256 hash of the desired block")]
              :return {:message String :payload [String]}
              :summary "fetch a list of block hashes after the provided hash on the chain."
              (ok (h/handler {:message "get_blocks_since" :payload block-hash} {})))

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
               (ok {:pong ping}))

   (sweet/POST "/balance" req
               :return {:message String :payload {:address s/Str :balance s/Int}}
               :body-params [address :- s/Str]
               :summary "Get balance for a given ClarkeCoin address (DER-encoded RSA public key)."
               (ok (h/handler {:message "get_balance" :payload address} {})))

   (sweet/POST "/blocks" req
               :return {:message String :payload Block}
               :body [block Block]
               :summary "Submit a new block to this node for inclusion in the chain."
               (let [resp (h/handler {:message "submit_block" :payload block} {})]
                     (if (= "block-accepted" (:message resp))
                       (do (log/info "Accepted new block" (q/bhash block))
                           (ok resp))
                       (do (log/info "Rejected block" (q/bhash block) "with errors" (:payload resp))
                         (bad-request resp)))))

   (sweet/POST "/pending_transactions" req
               :return {:message String :payload Transaction}
               :body [transaction Transaction]
               :summary "Submit a new transaction to this node for inclusion in the next block."
               (let [resp (h/handler {:message "submit_transaction" :payload transaction} {})]
                     (if (= "transaction-accepted" (:message resp))
                       (ok resp)
                       (bad-request resp))))

   (sweet/POST "/unsigned_payment_transactions" req
               :return {:message String :payload UnsignedTransaction}
               :body-params [from_address :- s/Str
                             to_address :- s/Str
                             amount :- s/Int
                             fee :- s/Int]
               :summary "Submit a new transaction to this node for inclusion in the next block."
               (let [resp (h/handler {:message "generate_payment" :payload {:from-address from_address
                                                                            :to-address to_address
                                                                            :amount amount
                                                                            :fee fee}} {})]
                 (if (= "transaction-accepted" (:message resp))
                   (ok resp)
                   (bad-request resp))))

   (sweet/POST "/unmined_block" req
               :return {:message String :payload Block}
               :body [data (s/maybe {:address s/Str})]
               :summary "Request an unmined block from this node's Txn Pool with the coinbase assigned to the provided Addr."
               (ok (h/handler {:message "generate_unmined_block" :payload (if (:address data)
                                                                            (:address data)
                                                                            (q/wallet-addr @db/db))} {})))
   (route/not-found {:status 404 :body {:error "not found"}})))

(defonce server (atom nil))

(defn stop!
  "When httpkit starts a server it returns a function that stops the server.
   This function is what we store into the server atom in start!, so now we simply
   retrieve this and invoke it."
  ([] (if-let [server @server] (server))))

(defn debug-logger [handler]
  (fn [request]
    (println "HTTP REQ: " (:request-method request) (:uri request))
    (if (:body request)
      (let [b (slurp (.bytes (:body request)))]
        (println "GOT BODY: " b)
        (->> (org.httpkit.BytesInputStream. (.getBytes b) (count b))
            (assoc request)
            (handler)))
      (handler request))))

(def print-mw (fn [h] (fn [r] (println r) (h r))))
(def with-middleware
  (-> (routes web-ui api)
      (identity)
      #_(debug-logger)))

(defn start!
  ([] (start! 3001))
  ([port]
   (println "HTTP Starting with port: " port)
   (stop!)
   (reset! server (httpkit/run-server #'with-middleware {:port port}))))
