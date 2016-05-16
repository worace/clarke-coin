(ns block-chain.transactions
  (:require [block-chain.utils :refer :all]
            [block-chain.wallet :as wallet]
            [block-chain.queries :as q]
            [block-chain.db :as db]
            [cheshire.core :as json]))

(def coinbase-reward 25)
(def input-signable (partial cat-keys [:source-hash :source-index]))
(def input-hashable (partial cat-keys [:source-hash :source-index]))
(def output-signable (partial cat-keys [:amount :address]))
(def output-hashable output-signable)

(defn txn-signable [txn]
  (apply str (concat (map input-signable (:inputs txn))
                     (map output-signable (:outputs txn))
                     [(:min-height txn) (:timestamp txn)])))

(defn txn-hashable [txn]
  (apply str (concat (map input-hashable (:inputs txn))
                     (map output-hashable (:outputs txn))
                     [(:min-height txn) (:timestamp txn)])))

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

(defn sign-txn
  "Takes a transaction map consisting of :inputs and :outputs, where each input contains
   a Source TXN Hash and Source Output Index. Signs each input by adding :signature
   which contains an RSA-SHA256 signature of the JSON representation of all the outputs in the transaction."
  [txn private-key]
  (let [signable (txn-signable txn)]
    (assoc txn
           :inputs
           (into [] (map (fn [i] (assoc i :signature (wallet/sign signable private-key)))
                         (:inputs txn))))))

(defn txn-fees
  "Finds available txn-fees from a pool of txns by finding the diff
   between cumulative inputs and cumulative outputs"
  [txns db]
  (let [sources (map (partial q/source-output db)
                     (mapcat :inputs txns))
        outputs (mapcat :outputs txns)]
    (- (reduce + (map :amount sources))
       (reduce + (map :amount outputs)))))

(defn coinbase
  "Generate new 'coinbase' mining reward transaction for the given
   address and txn pool. Coinbase includes no inputs and 1 output,
   where the address of the output is the address provided and the
   amount of the output is "
  ([] (coinbase (:address wallet/keypair)))
  ([address] (coinbase address @db/db))
  ([address db]
   (tag-coords
    (hash-txn
     {:inputs []
      :min-height (q/chain-length db)
      :outputs [{:amount (+ coinbase-reward
                            (txn-fees (q/transaction-pool db) db))
                 :address address}]
      :timestamp (current-time-millis)}))))
