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
(def base-url "http://localhost:9292")

(use-fixtures :once with-server)

(defn post-req [path data]
  (update
   (http/post (str base-url path)
             {:form-params data
              :content-type :json})
   :body
   read-json))

(defn get-req [path]
  (http/get (str base-url path)))

(deftest test-echo
  (let [r (post-req "/echo" {:hello "there"})]
    (is (= {:message "echo" :payload {:hello "there"}}
           (:body r)))
    (is (= "application/json; charset=utf-8"
           (get-in r [:headers "Content-Type"])))))
(deftest test-ping
  (let [r (post-req "/ping" {:hello "there"})]
    (is (= {:message "pong" :payload {:hello "there"}}
           (:body r)))))
