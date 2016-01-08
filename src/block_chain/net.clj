(ns block-chain.net
  (:require #_[aleph.tcp :as tcp]
            #_[manifold.stream :as s]
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


(defn gets
  "Read a line of textual data from the given socket"
  [reader]
  (.readLine reader))

(defn read-lines [reader]
  (loop [lines []
         next-line (gets reader)]
    (if (empty? next-line)
      lines
      (recur (conj lines next-line)
             (gets reader)))))

(def kill-switch (atom true))

(defn serve-loop [port handler]
  (async/go
    (with-open [server-sock (ServerSocket. port)]
      (try
        (while @kill-switch
          (println "will wait for next request")
          (with-open [socket (.accept server-sock)
                      stream (io/reader socket)]
            (println "got connection: " socket)
            (write-response socket
                            (handler
                             (read-lines stream)))))
        (catch
            Exception
            e
          (println "caught exception: " (.getMessage e)))))))


(try
     (/ 1 0)
     (catch Exception e (str "caught exception: " (.getMessage e))))
(defn handler [lines]
  (println "handling lines: " lines)
  (first lines))

;;echo handler:
#_(serve 9000 (partial apply str))
#_(let [msg-in (receive sock)
          msg-out (handler msg-in)]
      (write-response sock msg-out))

