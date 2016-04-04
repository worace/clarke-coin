(ns block-chain.net
  (:require [clojure.java.io :as io]
            [clojure.core.async :as async]
            [block-chain.utils :refer :all]
            [block-chain.db :as db]
            [block-chain.message-handlers :as m])
  (:import  [java.net ServerSocket Socket]))

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

(defn socket-info [socket]
  {:remote-address (.getHostAddress (.getInetAddress socket))
   :local-port (.getPort socket)
   :outgoing-port (.getPort socket)})

(defn serve-loop [server-sock handler]
  (future
    (while true
      (with-open [socket (.accept server-sock)]
        (write-response socket
                        (handler (read-lines socket)
                                 (socket-info socket)))))))

(defn start-server [port handler]
  (let [server (ServerSocket. port)]
    {:server server :run-loop (serve-loop server handler)}))

(defonce server (atom nil))
(def default-port 8334)

(defn handler [lines socket-info]
  (let [msg (read-json (first lines))]
    (msg-string (m/handler msg socket-info))))

(defn start!
  ([] (start! default-port))
  ([port] (reset! server (start-server port handler))))

(defn shutdown! []
  (if (:server @server)
    (.close (:server @server))))

(defn restart! []
  (shutdown!)
  (start!))
