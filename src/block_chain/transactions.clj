(ns block-chain.transactions
  (:require [pandect.algo.sha256 :refer [sha256]]
            [cheshire.core :as json]))

;; sample txn format:
#_{:inputs [{:source-hash "original txn hash"
             :source-index 0
             :signature "pizza"}]
   :outputs [{:amount 5
              :address "(PUBLIC KEY)"}]}

(defn cat-keys
  "take a map and a vector of keys and create a concatenated
   string of the value for each key"
  [keys m]
  (apply str (map (partial get m) keys)))

(def input-signable (partial cat-keys [:source-hash :source-index]))
(def input-hashable (partial cat-keys [:source-hash :source-index :signature]))
(def output-signable (partial cat-keys [:amount :address]))
(def output-hashable output-signable)

(defn txn-signable [txn]
  (apply str (concat (map input-signable (:inputs txn))
                     (map output-signable (:outputs txn)))))

(defn txn-hashable [txn]
  (apply str (concat (map input-hashable (:inputs txn))
                     (map output-hashable (:outputs txn)))))

(defn txn-hash [txn]
  (sha256 (txn-hashable txn)))

(defn serialize-txn [txn]
  (json/generate-string txn))

(defn read-txn [txn-json]
  (json/parse-string txn-json true))
