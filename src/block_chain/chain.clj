(ns block-chain.chain
  (:require [clojure.java.io :as io]
            [block-chain.utils :refer :all]))

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
  ([hash c]
   (first (filter #(= hash (:hash %)) c))))
