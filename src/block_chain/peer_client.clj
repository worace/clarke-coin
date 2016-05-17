(ns block-chain.peer-client
  (:require [clj-http.client :as http]
            [block-chain.log :as log]
            [block-chain.utils :refer :all]))

(defn url [peer & path-segments]
  (str "http://" (:host peer) ":" (:port peer) "/" (clojure.string/join "/" path-segments)))

(defn req
  ([verb url] (req verb url {}))
  ([verb url params]
   (try
     (-> (case verb
           :get (http/get url)
           :post (http/post url {:form-params params
                                 :throw-exceptions false
                                 :content-type :json}))
         :body
         read-json
         :payload)
     (catch Exception e (do (log/info "PEER CLIENT ERROR: " (.getMessage e))
                            {})))))

(defn block-height [peer]
  (req :get (url peer "block_height")))

(defn blocks-since [peer block-hash]
  (req :get (url peer "blocks_since" block-hash)))

(defn block [peer block-hash]
  (req :get (url peer "blocks" block-hash)))

(defn send-peer [peer port]
  (req :post (url peer "peers") {:port port}))

(defn send-block [peer block]
  (req :post (url peer "blocks") block))

(defn send-txn [peer txn]
  (req :post (url peer "pending_transactions") txn))
