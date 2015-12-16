(ns block-chain.chain
  (:require [clojure.java.io :as io]))

(def chain-path (str (System/getProperty "user.home")
                     "/.block_chain.txt"))

(defn read-stored-chain []
  (if (.exists (io/as-file chain-path))
    []
    []))

(defonce block-chain (atom (read-stored-chain)))

(defn add-block [])

(defn block-by-hash [hash] )

(defn block-by-height [i])

;; saving mined blocks
;; need to store them somewhere -- block-chain ns?
;; block-chain functions
;; -- add block
;; -- find block by hash
;; -- find block by height
