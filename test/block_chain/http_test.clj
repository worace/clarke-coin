(ns block-chain.http-test
  (:require [clojure.test :refer :all]
            [clj-http.client :as http]
            [block-chain.http :as server]
            [block-chain.utils :refer :all]
            [clojure.pprint :refer [pprint]]))

(defn with-server [f]
  (server/start! 9292)
  (f)
  (server/stop!))

(use-fixtures :once with-server)

(defn post-req [path data]
  (update
   (http/post (str "http://localhost:9292" path)
             {:form-params data
              :content-type :json})
   :body
   read-json))

(defn get-req [path]
  (http/get (str "http://localhost:9292" path)))

(deftest test-echo
  (let [r (post-req "/echo" {:hello "there"})]
    (is (= {:hello "there"}
           (:body r)))
    (is (= "application/json; charset=utf-8"
           (get-in r [:headers "Content-Type"])))))
