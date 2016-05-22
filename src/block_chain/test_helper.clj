(ns block-chain.test-helper
  (:import [java.util UUID])
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

;; File helpers thanks to clojure cookbook
(defn safe-delete [file-path]
  (if (.exists (clojure.java.io/file file-path))
    (try
      (clojure.java.io/delete-file file-path)
      (catch Exception e (str "exception: " (.getMessage e))))
    false))

(defn delete-directory [directory-path]
  (let [directory-contents (file-seq (clojure.java.io/file directory-path))
        files-to-delete (filter #(.isFile %) directory-contents)]
    (doseq [file files-to-delete]
      (safe-delete (.getPath file)))
    (safe-delete directory-path)))


(defn temp-db-conn []
  (let [path (str "/tmp/" (UUID/randomUUID))]
    (clear-db-path! path)
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. #(delete-directory path)))
    (db/conn path)))
