(ns block-chain.transactions
  (:require [pandect.algo.sha256 :refer [sha256]]
            [block-chain.utils :refer :all]
            [cheshire.core :as json]))

;; sample txn format:
#_{:inputs [{:source-hash "original txn hash"
             :source-index 0
             :signature "pizza"}]
   :outputs [{:amount 5
              :address "(PUBLIC KEY)"}]
   :timestamp (current-time-millis)}


(def input-signable (partial cat-keys [:source-hash :source-index]))
(def input-hashable (partial cat-keys [:source-hash :source-index :signature]))
(def output-signable (partial cat-keys [:amount :address]))
(def output-hashable output-signable)

(defn txn-signable [txn]
  (apply str (concat (map input-signable (:inputs txn))
                     (map output-signable (:outputs txn))
                     )))

(defn txn-hashable [txn]
  (apply str (concat (map input-hashable (:inputs txn))
                     (map output-hashable (:outputs txn))
                     [(:timestamp txn)])))

(defn serialize-txn [txn]
  (write-json txn))

(defn read-txn [txn-json]
  (read-json txn-json))

(defn txn-hash [txn]
  (sha256 (txn-hashable txn)))

(defn hash-txn [txn]
  (assoc txn :hash (txn-hash txn)))

(defn gather-transactions
  "Gather pending transactions from the network and add our own coinbase
   reward. (Currently just injecting the coinbase since we don't have other
   txns available yet)"
  []
  [])
