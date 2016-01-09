(ns block-chain.message-handlers
  (:require [block-chain.utils :refer :all]
            [block-chain.peers :as peers]))

(defn echo [msg sock-info]
  msg)

(defn pong [msg sock-info]
  (println "ponging")
  {:message-type "pong" :payload (:payload msg)})

(defn get-peers [msg sock-info]
  {:message-type "peers" :payload (peers/get-peers)})

(defn add-peer [msg sock-info]
  (let [host (:remote-address sock-info)
        port (:port (:payload msg))]
    (peers/add-peer! {:host host :port port})
    {:message-type "peers" :payload (peers/get-peers)}))

(def message-handlers
  {"echo" echo
   "ping" pong
   "get_peers" get-peers
   "add_peer" add-peer})

(defn handler [msg sock-info]
  (let [handler-fn (get message-handlers
                        (:message-type msg)
                        echo)]
    (handler-fn msg sock-info)))
