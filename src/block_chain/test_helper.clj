(ns block-chain.test-helper
  (:import [java.util UUID])
  (:require [block-chain.db :as db]
            [clj-leveldb :as ldb]
            [block-chain.queries :as q]))

(defn clear-db-path! [path] (ldb/destroy-db path))

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
