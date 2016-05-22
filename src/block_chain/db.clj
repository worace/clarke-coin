(ns block-chain.db
  (:require [clojure.java.io :as io]
            [block-chain.utils :refer :all]
            [block-chain.queries :as q]
            [environ.core :refer [env]]
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
(defn bytes->str [bytes] (apply str (map char bytes)))

(defn close [leveldb] (.close leveldb))

(def db-path (env :db-path))
(defn conn [path]
  (ldb/create-db path
                 {:val-encoder json->bytes
                  :key-decoder bytes->str
                  :val-decoder bytes->json}))

(defn db-map [conn]
  {:block-db conn
   :default-key wallet/keypair
   :children {}
   :chains {}
   :peers #{}
   :transaction-pool #{}
   :transactions {}})

#_(str "/tmp" #_(env :db-dir) "/" "clarke-db" label)
(defn make-db [path] (db-map (conn path)))

;; (defonce empty-db  (make-db "/tmp/clarke-db-empty"))
(def empty-db {:blocks {}
               :default-key wallet/keypair
               :children {}
               :chains {}
               :peers #{}
               :transaction-pool #{}
               :transactions {}})

;; (defonce initial-db (-> (make-db "/tmp/clarke-db")
;;                         (q/add-block genesis-block)))
(def initial-db empty-db)

;; (def db (atom initial-db))
(def db (atom {}))

(defn wipe-db! [ldb-conn]
  (apply (partial ldb/delete ldb-conn)
         (map first (ldb/iterator ldb-conn))))
