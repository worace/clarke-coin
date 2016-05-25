(ns block-chain.db
  (:require [clojure.java.io :as io]
            [block-chain.utils :refer :all]
            [block-chain.queries :as q]
            [environ.core :refer [env]]
            [block-chain.wallet :as wallet]
            [clj-leveldb :as ldb]
            ))

(def genesis-block (read-json (slurp (io/resource "genesis.json"))))

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
