(ns block-chain.test-helper
  (:require [block-chain.db :as db]
            [clj-leveldb :as ldb]
            [block-chain.queries :as q]))

(defn with-empty-db [test]
  (db/wipe-db! (:block-db db/empty-db))
  (test)
  (db/wipe-db! (:block-db db/empty-db)))

(defn restore-initial-db! []
  (db/wipe-db! (:block-db db/initial-db))
  (q/add-block db/initial-db db/genesis-block))

(defn restore-empty-db! []
  (db/wipe-db! (:block-db db/empty-db)))

(defn clear-db-path! [path] (ldb/destroy-db path))

(defn dashed-ns-name []
  (clojure.string/replace (name (ns-name *ns*)) #"\." "-"))

(defn close-conn! [db]
  (.close (:block-db db)))

(defn close-conns! [& dbs]
  (doseq [db dbs] (close-conn! db)))
