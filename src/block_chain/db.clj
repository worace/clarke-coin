(ns block-chain.db
  (:require [clojure.java.io :as io]
            [block-chain.utils :refer :all]
            [block-chain.queries :as q]
            ))

;; DB namespace
;; * collect various atom / ref types from project
;; * remove implicit local references to stateful atoms
;;   in varous namespaces
;; * rewrite any atom-dependent functions to take data types
;;   as explicit inputs
;; * return matching data structure as output? OR take actual
;;   atom and perform the transaction within the function

;; Data Atoms:
;; * chain.clj block-chain - (atom [])
;; * peers.clj - peers - (atom #{})
;; * transactions.clj - transactions -  (atom #{})

;; Config / Switches
;; * core.clj - running? - (atom true) (clean this out)
;; * miner.clj - mine? (atom true)
;; * net.clj - server - (atom nil)

(def genesis-block (read-json (slurp "./genesis.json")))

(def chain-path (str (System/getProperty "user.home")
                     "/.block_chain.json"))

(def peers (atom #{}))
(defonce transaction-pool (atom #{}))
(def empty-db {:blocks {}
               :children {}
               :chains {}
               :peers #{}
               :transaction-pool #{}
               :transactions {}})
(def initial-db (q/add-block empty-db genesis-block))
(defonce db (atom initial-db))
