(ns block-chain.block-sync-test
  (:require [clojure.test :refer :all]
            [block-chain.peer-client :as pc]
            [block-chain.utils :refer :all]
            [block-chain.miner :as miner]
            [block-chain.queries :as q]
            [block-chain.db :as db]
            [org.httpkit.server :as httpkit]
            [compojure.core :refer [defroutes GET]]
            [block-chain.test-helper :as th]
            [block-chain.block-sync :refer :all]))

(def peer-db (atom nil))
(def our-db (atom nil))

(defn setup [tests]
  (with-open [our-conn (th/temp-db-conn)
              peer-conn (th/temp-db-conn)]
    (reset! peer-db (db/db-map peer-conn))
    (reset! our-db (db/db-map our-conn))
    (q/add-block! peer-db db/genesis-block)
    (q/add-block! our-db db/genesis-block)
    (dotimes [_ 6] (miner/mine-and-commit-db! peer-db))
    (tests)))

(use-fixtures :once setup)


(deftest test-setup2
  (is (= (q/bhash db/genesis-block) (q/highest-hash @our-db)))
  (is (= 7 (q/chain-length @peer-db)))
  (is (= 1 (q/chain-length @our-db))))

(defn blocks-since-handler [hash]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body (write-json
          {:payload (map q/bhash (q/blocks-since @peer-db hash))})})

(defn block-handler [hash]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body (write-json {:payload (q/get-block @peer-db hash)})
   })

(defroutes peer-handler
  (GET "/blocks_since/:hash" [hash] (blocks-since-handler hash))
  (GET "/blocks/:hash" [hash] (block-handler hash)))

(deftest test-wtf
  (is (= (q/bhash db/genesis-block) (first (reverse (map q/bhash (q/longest-chain @peer-db)))))))

(deftest test-synced-chain-adds-blocks-from-peer
  (let [shutdown-fn (httpkit/run-server peer-handler {:port 9292})
        peer {:host "localhost" :port "9292"}]
    (try
      (is (= (q/bhash db/genesis-block) (q/bhash (last (q/longest-chain @peer-db)))))
      (is (= 6 (count (pc/blocks-since peer (q/bhash (last (q/longest-chain @peer-db)))))))
      (is (= (q/longest-chain @peer-db)
             (q/longest-chain (synced-chain @our-db peer))))
      (finally (shutdown-fn)))))
