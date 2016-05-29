(ns block-chain.core
  (:gen-class)
  (:require [block-chain.miner :as miner]
            [block-chain.db :as db]
            [block-chain.queries :as q]
            [block-chain.peer-client :as pc]
            [block-chain.block-sync :as bsync]
            [block-chain.log :as log]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.tools.nrepl.server :as repl]
            [block-chain.http :as http]))

(defonce repl-server (atom nil))

(defn start-repl! [port]
  (if @repl-server
    (repl/stop-server @repl-server))
  (reset! repl-server (repl/start-server :port port)))

(defn bootstrap-from-peer! [peer our-port]
  (q/add-peer! db/db peer)
  (pc/send-peer peer our-port)
  (log/info "Will bootstrap from peer node: " peer)
  (bsync/sync-if-needed! db/db peer))

(defn bootstrap-from-dns-server! [url our-port]
  (log/info "Will bootstrap from dns-server" url our-port)
  (doseq [p (pc/dns-request-peers url our-port)]
    (bootstrap-from-peer! p our-port)))

(defn start! [{port :port
               repl-port :repl-port
               peer :bootstrap-peer
               dns-server :dns-server
               :as opts}]
  (log/info "****** Starting Clarke Coin *******")
  (log/info "Received CLI opts:" opts)
  (log/info "Will set up initial DB at path" db/db-path)
  (reset! db/db (db/make-initial-db db/db-path))
  (http/start! port)
  (when peer (bootstrap-from-peer! peer port))
  (when dns-server (bootstrap-from-dns-server! dns-server port))
  (when repl-port (start-repl! repl-port))
  (miner/run-miner!))

(defn stop! []
  (log/info "****** Stopping Clarke Coin *******")
  (miner/stop-miner!)
  (http/stop!)
  (repl/stop-server @repl-server))

(def cli-options
  [["-p" "--port PORT" "Port for HTTP Server"
    :default 3000
    :parse-fn #(Integer/parseInt %)]
   ["-r" "--repl-port PORT" "Port for embedded REPL Server"
    :default nil
    :parse-fn #(Integer/parseInt %)]
   ["-b" "--bootstrap-peer PEER" "Peer host and port to bootstrap from"
    :default nil
    :parse-fn (fn [p] {:host (first (clojure.string/split p #":"))
                       :port (last (clojure.string/split p #":"))})]
   ["-d" "--dns-server URL" "URL for a DNS server to use for discovering initial peers"
    :default nil]
   ["-h" "--help"]])

(defn -main [& args]
  (let [opts (parse-opts args cli-options)]
    (if (not (empty? (:errors opts)))
      (println "Invalid CLI Options: " (:errors opts))
      (do
        (start! (:options opts))
        (.addShutdownHook
         (Runtime/getRuntime)
         (Thread. stop!))
        (while true (Thread/sleep 15000))))))
