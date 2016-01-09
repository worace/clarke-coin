(ns block-chain.net
  (:require [clojure.java.io :as io]
            [clojure.core.async :as async])
  (:import  [java.net ServerSocket]))

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

(defn read-lines [reader]
  (loop [lines []
         next-line (.readLine reader)]
    (if (empty? next-line)
      lines
      (recur (conj lines next-line)
             (.readLine reader)))))

(defn echo-handler [lines]
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

