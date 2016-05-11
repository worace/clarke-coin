(ns block-chain.db
  (:require [clojure.java.io :as io]
            [block-chain.utils :refer :all]
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

(defonce block-chain (atom [genesis-block]))

(def chain-path (str (System/getProperty "user.home")
                     "/.block_chain.json"))

(defn read-stored-chain
  [path] (if (.exists (io/as-file path))
           (into [] (read-json (slurp path)))
           []))

(defn load-block-chain!
  ([] (load-block-chain! chain-path))
  ([path] (reset! block-chain (read-stored-chain path))))

(defn write-block-chain!
  ([] (write-block-chain! chain-path @block-chain))
  ([path blocks] (spit path (write-json blocks))))

(def peers (atom #{}))
(defonce transaction-pool (atom #{}))
