(ns block-chain.peer-client
  (:require [clj-http.client :as http]
            [block-chain.log :as log]
            [block-chain.queries :as q]
            [block-chain.utils :refer :all]))

(defn url [peer & path-segments]
  (str "http://" (:host peer) ":" (:port peer) "/" (clojure.string/join "/" path-segments)))

(defn req
  ([verb url] (req verb url {}))
  ([verb url params] (req verb url params {}))
  ([verb url params default]
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
                            default)))))

(defn block-height [peer]
  (req :get (url peer "block_height") 0))

(defn blocks-since [peer block-hash]
  (req :get (url peer "blocks_since" block-hash)))

(defn block [peer block-hash]
  (req :get (url peer "blocks" block-hash)))

(defn send-peer [peer port]
  (req :post (url peer "peers") {:port port}))

(defn send-block [peer block]
  (log/info "Sending block" (q/bhash block) "to peer" peer)
  (req :post (url peer "blocks") block))

(defn send-txn [peer txn]
  (req :post (url peer "pending_transactions") txn))

(defn unmined-block [peer addr]
  (req :post (url peer "unmined_block") {:address addr}))
