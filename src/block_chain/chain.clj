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

(def block-chain (atom []))

(defn load-chain! []
  (reset! block-chain (read-stored-chain)))

(defn write-chain!
  ([] (write-chain! chain-path))
  ([path] (spit path (write-json @block-chain))))

(defn add-block! [b]
  (swap! block-chain conj b))

(defn block-by-hash
  ([hash] (block-by-hash hash @block-chain))
  ([hash c] (first (filter #(= hash (:hash %)) c))))

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
   of recent blocks. Currently just setting a static (easy) target
   until we have more blocks in place to pull frequency data from."
  []
  (let [recent-blocks (take-last 10 @block-chain)]
    (if (> (count recent-blocks) 1)
      (target/adjusted-target recent-blocks 15)
      default-target)))
