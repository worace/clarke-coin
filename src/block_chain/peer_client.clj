(ns block-chain.peer-client
  (:require [clj-http.client :as http]
            [block-chain.utils :refer :all]))

(defn url [peer & path-segments]
  (str "http://" (:host peer) ":" (:port peer) "/" (clojure.string/join "/" path-segments)))

(defn block-height [peer]
  (-> (url peer "block_height")
      (http/get)
      (:body)
      (read-json)
      (:payload)))

(defn blocks-since [peer block-hash]
  (-> (url peer "blocks_since" block-hash)
      (http/get)
      (:body)
      (read-json)
      (:payload)))

(defn block [peer block-hash]
  (-> (url peer "blocks" block-hash)
      (http/get)
      (:body)
      (read-json)
      (:payload)))

