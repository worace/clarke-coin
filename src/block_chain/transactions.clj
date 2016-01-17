(ns block-chain.transactions
  (:require [pandect.algo.sha256 :refer [sha256]]
            [block-chain.utils :refer :all]
            [debugger.core :as dbg]
            [cheshire.core :as json]))

(defonce txn-pool (atom #{}))
(defn pool []
  (into [] @txn-pool))

(defn add! [txn]
  ;;TODO - verify txn here
  (swap! txn-pool conj txn))

(defn clear-pool! [] (reset! txn-pool #{}))

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

(defn tag-output [output index hash]
  "Adds an additional :coords key to the output containing
   its transaction-id (the txn hash) and index. This simplifies
   the process of working with outputs further down the pipeline
   as we don't have to have to refer back to the output's
   transaction context as frequently."
  (assoc output :coords
         {:transaction-id hash
          :index index}))

(defn tag-coords [txn]
  (let [tagged-outputs (map tag-output
                            (:outputs txn)
                            (range (count (:outputs txn)))
                            (repeat (:hash txn)))]
    (assoc txn :outputs (into [] tagged-outputs))))

(defn gather-transactions
  "Gather pending transactions from the network and add our own coinbase
   reward. (Currently just injecting the coinbase since we don't have other
   txns available yet)"
  []
  [])
