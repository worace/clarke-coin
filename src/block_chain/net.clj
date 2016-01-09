(ns block-chain.net
  (:require [aleph.tcp :as tcp]
            [net.async.tcp :as nettcp]
            [manifold.stream :as s]
            [clojure.java.io :as io]
            [clojure.core.async :as async])
  (:import  [java.net ServerSocket]))


;; (defn echo-handler [s info]
;;   (s/connect s s))

;; (defn server [port]
;;   (tcp/start-server echo-handler {:port 10001}))

;; (defn client [host port]
;;   @(tcp/client {:host host :port port}))

;; (defn listen [client handler]
;;   (async/go
;;     (loop [resp @(s/take! client)]
;;       (println "got resp: " (apply handler resp))
;;       (recur @(s/take! client)))
;;     (println "stopping listen loop")))


(defn gets
  "Read a line of textual data from the given socket"
  [reader]
  (.readLine reader))

(defn write-response
  "Send the given string message out over the given socket"
  [socket msg]
  (let [writer (io/writer socket)]
      (.write writer msg)
      (.flush writer)))

(defn serve [port handler]
  (with-open [server-sock (ServerSocket. port)
              socket (.accept server-sock)
              stream (io/reader socket)]
    (loop [lines []
           next-line (gets stream)]
      (if (empty? next-line)
        (write-response socket (handler lines))
        (recur (conj lines next-line)
               (gets stream))))))


(let [server-sock (ServerSocket. 8335)]
  (println "server waiting for conn")
  (let [client-sock (.accept server-sock)
        reader (io/reader client-sock)]
    (println "received conn: " client-sock)
    (loop [lines []
           next-line (.readLine reader)]
      (println "read line: " next-line)
      (if (empty? next-line)
        (let [writer (io/writer client-sock)]
          (println "received end of message")
          (println "will send lines to client: " lines)
          (.write writer (clojure.string/join "\n" lines))
          (.close writer)
          (.close client-sock))
        (recur (conj lines next-line)
               (.readLine reader)))))
  (println "exiting server")
  (.close server-sock))

(defn read-lines [reader]
  (loop [lines []
         next-line (.readLine reader)]
    (if (empty? next-line)
      lines
      (recur (conj lines next-line)
             (.readLine reader)))))

(defn handler [lines]
  (println "handling lines: " lines)
  (clojure.string/join "\n" lines))

(import '[java.net ServerSocket])
(require '[clojure.java.io :as io])
(defn start-server [port handler]
  (let [server-sock (ServerSocket. port)]
    (println "server waiting for will wait for next request")
    {:server server-sock
     :run-loop (future
      (while true
        (with-open [socket (.accept server-sock)
                    reader (io/reader socket)
                    writer (io/writer socket)]
          (println "got connection: " socket)
          (let [lines (read-lines reader)]
            (println "read lines: " lines)
            (.write writer
                    (handler lines))))))
     }))

;;echo handler:
#_(serve 9000 (partial apply str))
#_(let [msg-in (receive sock)
          msg-out (handler msg-in)]
      (write-response sock msg-out))

#_(fn [sock info]
                      (println "sock: " sock)
                      (println "info: " info)
                      (s/consume #(println %) sock)
                      (s/put! sock "hello")
                      (s/close! sock))

;; (def server
;;   (tcp/start-server #_(fast-echo-handler inc)
;;                     (fn [sock info]
;;                       (prn "hi")
;;                       (s/consume (fn [arg] (do
;;                                              (println "consuming: " @arg)
;;                                              (s/put! sock @arg)))
;;                                  sock))
;;                     {:port 8335}))

;; (let [c @(tcp/client {:host "localhost" :port 8335})]
;;   @(s/put! c "hi")
;;   (println "client receiving:")
;;   (println (apply str (map char @(s/take! c)))))

;; (.close server)

(def server (nettcp/accept (nettcp/event-loop) {:port 9988}))

(let [conn (async/<!! (:accept-chan server))]
  (println "got conn: " conn)
  (loop [msg (async/<!! conn)]
    (if msg
      (do (println msg)
          (recur (async/<!! conn)))
      "closed")))
