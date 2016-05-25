(ns block-chain.db
  (:require [clojure.java.io :as io]
            [block-chain.utils :refer :all]
            [block-chain.queries :as q]
            [environ.core :refer [env]]
            [block-chain.wallet :as wallet]
            [clojure.tools.namespace.repl]
            [clj-leveldb :as ldb]
            ))

(clojure.tools.namespace.repl/disable-reload!)

(def genesis-block (read-json (slurp (io/resource "genesis.json"))))

(defn json->bytes [val]
  (-> val
      write-json
      .getBytes))

(defn bytes->json [bytes]
  (->> bytes (map char) (apply str) (read-json)))
(defn bytes->str [bytes] (apply str (map char bytes)))

(defn close [leveldb] (.close leveldb))

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

(defn make-db [path] (db-map (conn path)))

(defonce empty-db (make-db (env :db-path)))

(defonce initial-db (-> empty-db
                        (q/add-block genesis-block)))

(defonce db (atom initial-db))

(defn wipe-db! [ldb-conn]
  (apply (partial ldb/delete ldb-conn)
         (map first (ldb/iterator ldb-conn))))
