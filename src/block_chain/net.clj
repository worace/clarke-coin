(ns block-chain.net
  (:require [aleph.tcp :as tcp]
            [manifold.stream :as s]
            [clojure.core.async :as async]))


(defn echo-handler [s info]
  (s/connect s s))

#_(def server (tcp/start-server echo-handler {:port 10001}))
(def client @(tcp/client {:host "localhost" :port 10001}))

#_(async/go
  (loop [resp @(s/take! client)]
    (println "got resp: " (apply str (map char resp)))
    (recur @(s/take! client)))
  (println "stopping listen loop"))
