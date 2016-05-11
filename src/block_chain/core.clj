(ns block-chain.core
  (:gen-class)
  (:require [block-chain.miner :as miner]
            [block-chain.db :as db]
            [block-chain.block-sync :as bsync]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.tools.nrepl.server :as repl]
            [block-chain.http :as http]))

(defonce repl-server (atom nil))

(defn start-repl! [port]
  (if @repl-server
    (repl/stop-server @repl-server))
  (reset! repl-server (repl/start-server :port port)))

(defn start! [{port :port repl-port :repl-port peer :bootstrap-peer}]
  (println "****** Starting Clarke Coin *******")
  (if peer
    (do
      (println "Will bootstrap from peer node: " peer)
      (bsync/sync-if-needed! db/block-chain peer)))
  (miner/run-miner!)
  (http/start! port)
  (start-repl! repl-port))

(defn stop! []
  (println "****** Stopping Clarke Coin *******")
  (miner/stop-miner!)
  (http/stop!)
  (repl/stop-server @repl-server))

(def cli-options
  [["-p" "--port PORT" "Port for HTTP Server"
    :default 3000
    :parse-fn #(Integer/parseInt %)]
   ["-r" "--repl-port PORT" "Port for embedded REPL Server"
    :default 7888
    :parse-fn #(Integer/parseInt %)]
   ["-b" "--bootstrap-peer PEER" "Peer host and port to bootstrap from"
    :default nil
    :parse-fn (fn [p] {:host (first (clojure.string/split p #":"))
                       :port (last (clojure.string/split p #":"))})]
   ["-h" "--help"]])

(defn -main [& args]
  (println "STARTING WITH ARGS: " args)
  (let [opts (parse-opts args cli-options)]
    (if (not (empty? (:errors opts)))
      (println "Invalid CLI Options: " (:errors opts))
      (do
        (println "WILL START WITH OPTS: " opts)
        (start! (:options opts))
        (.addShutdownHook
         (Runtime/getRuntime)
         (Thread. stop!))
        (while true (Thread/sleep 15000))))))
