(ns block-chain.net
  (:require [clojure.java.io :as io]
            [clojure.core.async :as async]
            [block-chain.utils :refer :all])
  (:import  [java.net ServerSocket]))

(defn write-response
  "Send the given string message out over the given socket"
  [socket msg]
  (let [writer (io/writer socket)]
    (.write writer msg)
    (.flush writer)))

(defn read-lines [socket]
  (let [reader (io/reader socket)]
    (loop [lines []
         next-line (.readLine reader)]
      (if (empty? next-line)
        lines
        (recur (conj lines next-line)
               (.readLine reader))))))

(defn serve-loop [server-sock handler]
  (future
    (while true
      (with-open [socket (.accept server-sock)]
        (println "received req")
        (write-response socket
                        (handler (read-lines socket)))))))

(defn start-server [port handler]
  (let [server (ServerSocket. port)]
    (println "server waiting for will wait for next request")
    {:server server :run-loop (serve-loop server handler)}))

(defn echo [msg]
  (println "echoing msg: " msg)
  msg)

(defn pong [msg]
  (println "ponging")
  {:message_type "pong" :payload (:payload msg)})

(defn get-peers [msg]
  {:message_type "peers" :payload []})

(def message-handlers
  {"echo" echo
   "ping" pong
   "get_peers" get-peers})

(defonce server (atom nil))
(def default-port 8334)

(defn handler [lines]
  (let [msg (read-json (first lines))
        type (:message-type msg)
        handler (get message-handlers type echo)]
    (str (write-json (handler msg)) "\n\n")))

(defn start!
  ([] (start! default-port))
  ([port] (reset! server (start-server port handler))))

(defn shutdown! []
  (if (:server @server)
    (.close (:server @server))))

(defn restart! []
  (shutdown!)
  (start!))
