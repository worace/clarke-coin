(ns block-chain.net
  (:require [clojure.java.io :as io]
            [clojure.core.async :as async])
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

(defn echo-handler [lines]
  (println "handling lines: " lines)
  (str (clojure.string/join "\n" lines) "\n"))

(defn serve-loop [server-sock handler]
  (future
    (while true
      (with-open [socket (.accept server-sock)]
        (write-response socket
                        (handler (read-lines socket)))))))

(defn start-server [port handler]
  (let [server (ServerSocket. port)]
    (println "server waiting for will wait for next request")
    {:server server :run-loop (serve-loop server handler)}))

