(ns block-chain.test-helper
  (:import [java.util UUID])
  (:require [block-chain.db :as db]
            [block-chain.utils :refer :all]
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

(defn baby-peer [req-storage]
  (fn [req]
    (let [req (if (:body req)
                (assoc req :body (-> req :body .bytes slurp))
                req)
          body (:body req)]
      (swap! req-storage update (:uri req) conj req)
      (println "PEER HANDLER runing req" req)
      (case (:uri req)
        "/ping" {:status 200 :body (write-json {:pong (:ping (read-json body))})}
        {:status 200 :body body}))))
