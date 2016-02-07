(ns block-chain.core
  (:require [block-chain.miner :as miner]
            [block-chain.net :as net]))


(defn start! []
  (println "****** Starting Clarke Coin *******")
  (miner/run-miner!)
  (net/start!)
  (Thread/sleep 1500))

(defn stop! []
  (println "****** Stopping Clarke Coin *******")
  (miner/stop-miner!)
  (net/shutdown!))

(defn -main [& args]
  (start!)
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
