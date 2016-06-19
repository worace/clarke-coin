(ns block-chain.db-keys
  "Simple namespace to aggregate all of the key-generation logic for where we are
   storing things in leveldb. Often need to use these from various places so try to
   centralize them here."
  (:require [block-chain.utils :refer [sha256]]
            [clojure.string :refer [join]]))

(defn block [block-hash] (str "block:" block-hash))

(defn child-blocks [block-hash] (str "child-blocks:" block-hash))

(defn utxos-range-start [address] (str "utxo:" (sha256 address)))

(defn utxo [address txn-id index] (join ":" ["utxo" (sha256 address) txn-id index]))

(defn txn [txn-hash] (str "transaction:" txn-hash))

(def highest-hash "highest-hash")
(defn chain-length [block-hash] (str "chain-length:" block-hash))
(defn child-blocks [block-hash] (str "child-blocks:" block-hash))

