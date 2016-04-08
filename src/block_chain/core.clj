(ns block-chain.core
  (:gen-class)
  (:require [block-chain.miner :as miner]
            [block-chain.db :as db]
            [clojure.tools.nrepl.server :as repl]
            [block-chain.http :as http]))

(defonce repl-server (atom nil))

(defn start-repl! []
  (if @repl-server
    (repl/stop-server @repl-server))
  (reset! repl-server (repl/start-server :port 7888)))

(defn start! [& args]
  (println "****** Starting Clarke Coin *******")
  (miner/run-miner!)
  (http/start! (Integer. (or (first args) "3000")))
  (start-repl!))

(defn stop! []
  (println "****** Stopping Clarke Coin *******")
  (miner/stop-miner!)
  (http/stop!)
  (repl/stop-server @repl-server))

(defn -main [& args]
  (println "STARTING WITH ARGS: " args)
  (apply start! args)
  (.addShutdownHook
   (Runtime/getRuntime)
   (Thread. stop!))
  (while true (Thread/sleep 15000)))

;; boot tcp server
;; start miner

;; Building a Clarke Coin UI

;; Data Layer -- Use existing TCP protocol to get
;; data out of the actual client

;; building UI as separate process?
;; simple ring server with a client that connects
;; over TCP
