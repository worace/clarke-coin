(ns block-chain.peer-client
  (:require [clj-http.client :as http]
            [block-chain.log :as log]
            [block-chain.queries :as q]
            [block-chain.utils :refer :all]))

(defn url [peer & path-segments]
  (str "http://" (:host peer) ":" (:port peer) "/" (clojure.string/join "/" path-segments)))

(defn req
  ([verb url] (req verb url {}))
  ([verb url options] (req verb url options {}))
  ([verb url options default]
   (try
     (-> (case verb
           :get (http/get url options)
           :post (http/post url options))
         :body
         read-json
         :payload)
     (catch Exception e (do (log/info "PEER CLIENT ERROR: " (.getMessage e))
                            default)))))

(defn block-height [peer]
  (req :get (url peer "block_height") {} 0))

(defn blocks-since [peer block-hash]
  (req :get (url peer "blocks_since" block-hash)))

(defn block [peer block-hash]
  (req :get (url peer "blocks" block-hash)))

(defn send-peer [peer port]
  (log/info "Connect to peer:" peer "from port:" port)
  (req :post (url peer "peers") {:form-params {:port port}
                                 :content-type :json
                                 :socket-timeout 3000
                                 :throw-exceptions false}))

(defn send-block [peer block]
  (req :post (url peer "blocks") {:form-params block
                                  :throw-exceptions false
                                  :content-type :json}))

(defn send-txn [peer txn]
  (req :post (url peer "pending_transactions") {:form-params txn
                                                :throw-exceptions false
                                                :content-type :json}))

(defn ping [peer time]
  (log/info "Pinging peer" peer time)
  (-> (http/post (url peer "ping")
                 {:form-params {:ping time}
                  :socket-timeout 2000
                  :conn-timeout 2000
                  :content-type :json})
      :body
      read-json))

(defn available-peer? [peer]
  (log/info "available-peer?" peer)
  (let [t (current-time-millis)]
    (try
      (= t (:pong (ping peer t)))
      (catch Exception e false))))

(defn unmined-block
  ([peer] (unmined-block peer nil))
  ([peer addr]
  (if addr
    (req :post (url peer "unmined_block") {:form-params {:address addr}
                                           :throw-exceptions false
                                           :content-type :json})
    (req :post (url peer "unmined_block") {:throw-exceptions false
                                           :content-type :json}))))

(defn dns-request-peers [dns-server our-port]
  (assert (not (clojure.string/starts-with? dns-server "http")))
  (assert (not (clojure.string/ends-with? dns-server "/")))
  (let [body (-> (http/post (str "http://" dns-server "/api/recommended_peers")
                            {:form-params {:port our-port}
                             :content-type :json
                             :throw-exceptions true})
                 :body
                 read-json)]
    (if-let [e (:error body)]
      (do (log/info "Error fetching peers from DNS server:" e)
          [])
      body)))

(defn add-to-dns-registry [dns-server our-port]
  (assert (not (clojure.string/starts-with? dns-server "http")))
  (assert (not (clojure.string/ends-with? dns-server "/")))
  (let [body (-> (http/post (str "http://" dns-server "/api/peers")
                            {:form-params {:port our-port}
                             :content-type :json
                             :throw-exceptions false})
                 :body
                 read-json)]
    (if-let [e (:error body)]
      (do (log/info "Adding self to DNS registry:" e)
          {})
      body)))
