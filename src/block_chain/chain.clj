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

(defn clear! [] (reset! block-chain []))

(defn add-block! [b]
  (swap! block-chain conj b))

(defn block-by-hash
  ([hash] (block-by-hash hash @block-chain))
  ([hash c] (first (filter #(= hash (get-in % [:header :hash])) c))))

(defn transactions [blocks] (mapcat :transactions blocks))
(defn inputs [blocks] (mapcat :inputs (transactions blocks)))
(defn outputs [blocks] (mapcat :outputs (transactions blocks)))

(defn txn-by-hash
  [hash blocks]
  (first (filter #(= hash (get % :hash))
                 (transactions blocks))))

(defn source-output [input blocks]
  (if-let [t (txn-by-hash (:source-hash input)
                          blocks)]
    (get (:outputs t) (:source-index input))))

(defn latest-block-hash
  "Look up the hash of the latest block in the provided chain.
   Useful for getting parent hash for new blocks."
  [chain]
  (if-let [parent (last chain)]
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

(defn consumes-output?
  [source-hash source-index input]
  (and (= source-hash (:source-hash input))
       (= source-index (:source-index input))))

(defn unspent?
  "takes a txn hash and output index identifying a
   Transaction Output in the block chain. Searches the
   chain to find if this output has been spent."
  [blocks output]
  (let [inputs (inputs blocks)
        {:keys [transaction-id index]} (:coords output)
        spends-output? (partial consumes-output? transaction-id index)]
    (not-any? spends-output? inputs)))

(defn assigned-to-key? [key output]
  (= key (:address output)))

(defn unspent-outputs [key blocks]
  (->> (outputs blocks)
       (filter (partial assigned-to-key? key))
       (filter (partial unspent? blocks))))

(defn balance [key blocks]
  (reduce +
          (map :amount
               (unspent-outputs key blocks))))

(defn unspent-output-coords [key blocks]
  (mapcat (fn [txn]
            (mapcat (fn [output index]
                      (if (assigned-to-key? key output)
                        [{:source-hash (:hash txn) :source-index index}]
                        nil))
                    (:outputs txn)
                    (range (count (:outputs txn)))))
          (transactions blocks)))

(defn payment [amount from-key to-key blocks])
(defn broadcast-txn [txn])
