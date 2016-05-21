(ns block-chain.db
  (:require [clojure.java.io :as io]
            [block-chain.utils :refer :all]
            [block-chain.queries :as q]
            [block-chain.wallet :as wallet]
            [clj-leveldb :as ldb]
            ))

;; DB namespace
;; * collect various atom / ref types from project
;; * remove implicit local references to stateful atoms
;;   in varous namespaces
;; * rewrite any atom-dependent functions to take data types
;;   as explicit inputs
;; * return matching data structure as output? OR take actual
;;   atom and perform the transaction within the function

;; Data Atoms:
;; * chain.clj block-chain - (atom [])
;; * peers.clj - peers - (atom #{})
;; * transactions.clj - transactions -  (atom #{})

;; Config / Switches
;; * core.clj - running? - (atom true) (clean this out)
;; * miner.clj - mine? (atom true)
;; * net.clj - server - (atom nil)

(def genesis-block (read-json (slurp (io/resource "genesis.json"))))

(def chain-path (str (System/getProperty "user.home")
                     "/.block_chain.json"))

(defn json->bytes [val]
  (-> val
      write-json
      .getBytes))

(defn bytes->json [bytes]
  (->> bytes (map char) (apply str) (read-json)))

(defn close [leveldb] (.close leveldb))

(def db-path (str "/tmp/clarke-db-" (current-time-millis)))
(println "Making db at path:" db-path)
(def test-db (ldb/create-db db-path
                            {:val-encoder json->bytes
                             :val-decoder bytes->json}))

(def empty-db {:blocks {}
               :block-db test-db
               :default-key wallet/keypair
               :children {}
               :chains {}
               :peers #{}
               :transaction-pool #{}
               :transactions {}})
(println "CREATED EMPTY DB WITH" empty-db)
(def initial-db (q/add-block empty-db genesis-block))
(def db (atom initial-db))
