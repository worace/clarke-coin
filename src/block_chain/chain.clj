(ns block-chain.chain
  (:require [clojure.java.io :as io]
            [clojure.math.numeric-tower :as math]
            [pandect.algo.sha256 :refer [sha256]]
            [block-chain.utils :refer :all]
            [block-chain.target :as target]))

(def chain-path (str (System/getProperty "user.home")
                     "/.block_chain.json"))

(defn read-stored-chain
  ([] (read-stored-chain chain-path))
  ([path] (if (.exists (io/as-file path))
            (into [] (read-json (slurp path)))
            [])))

(defonce block-chain (atom []))

(defn load-chain!
  ([] (load-chain! chain-path))
  ([path] (reset! block-chain (read-stored-chain path))))

(defn write-chain!
  ([] (write-chain! chain-path @block-chain))
  ([path blocks] (spit path (write-json blocks))))

(defn add-block! [b]
  (swap! block-chain conj b))

(defn block-by-hash
  ([hash] (block-by-hash hash @block-chain))
  ([hash c] (first (filter #(= hash (get-in % [:header :hash])) c))))

(defn txn-by-hash
  [hash c]
  (first (filter #(= hash (get % :hash))
                 (mapcat :transactions c))))

(defn latest-block-hash
  "Look up the hash of the latest block in the chain.
   Useful for getting parent hash for new blocks."
  []
  (if-let [parent (last @block-chain)]
    (get-in parent [:header :hash])
    (hex-string 0)))

(def default-target (hex-string (math/expt 2 236)))

(defn next-target
  "Calculate the appropriate next target based on the time frequency
   of recent blocks."
  []
  (let [recent-blocks (take-last 10 @block-chain)]
    (if (> (count recent-blocks) 1)
      (target/adjusted-target recent-blocks 15)
      default-target)))

(defn balance [key blocks])
(defn unspent-outputs [key blocks])

(defn consumes-output?
  [source-hash source-index input]
  (and (= source-hash (:source-hash input))
       (= source-index (:source-index input))))

(defn unspent?
  "takes a txn hash and output index identifying a
   Transaction Output in the block chain. Searches the
   chain to find if this output has been spent."
  [source-hash source-index blocks]
  (let [inputs (mapcat :inputs (mapcat :transactions blocks))
        spends-output? (partial consumes-output? source-hash source-index)]
    (not-any? spends-output? inputs)))

(defn assigned-to-key? [txo key]
  (= (:address txo) key))

(defn payment [amount from-key to-key blocks])
(defn broadcast-txn [txn])
