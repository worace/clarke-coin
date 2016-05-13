(ns block-chain.block-sync-test
  (:require [clojure.test :refer :all]
            [block-chain.peer-client :as pc]
            [block-chain.utils :refer :all]
            [block-chain.miner :as miner]
            [block-chain.queries :refer [bhash]]
            [ring.adapter.jetty :as jetty]
            [compojure.core :refer [defroutes GET]]
            [block-chain.block-sync :refer :all]))

(def peer-chain (atom []))

;; A: 25
(miner/mine-and-commit peer-chain)
(miner/mine-and-commit peer-chain)
(miner/mine-and-commit peer-chain)
(miner/mine-and-commit peer-chain)
(miner/mine-and-commit peer-chain)
(miner/mine-and-commit peer-chain)
(miner/mine-and-commit peer-chain)

(def chain (atom (vec (take 1 @peer-chain))))

(deftest test-setup
  (is (= 7 (count @peer-chain)))
  (is (= 1 (count @chain))))

(defn blocks-since-handler [hash]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body (write-json {:payload (map bhash (drop 1 @peer-chain))})})

(defn block-handler [hash]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body (write-json {:payload (first (filter #(= hash (bhash %)) @peer-chain))})})

(defroutes peer-handler
  (GET "/blocks_since/:hash" [hash] (blocks-since-handler hash))
  (GET "/blocks/:hash" [hash] (block-handler hash)))

(deftest test-synced-chain-adds-blocks-from-peer
  (let [p (jetty/run-jetty peer-handler {:port 9292 :join? false})
        peer {:host "localhost" :port "9292"}]
    (try
      (is (= 6 (count (pc/blocks-since peer (bhash (first @chain))))))
      (is (= @peer-chain (synced-chain @chain peer)))
      (finally (.stop p)))))
