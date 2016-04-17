(ns block-chain.block-validations
  (:require [block-chain.blocks :as b]
            [block-chain.chain :as c]
            [block-chain.utils :refer :all]))

(defn valid-hash?
  ([block] (valid-hash? block []))
  ([block chain]
   (= (get-in block [:header :hash])
                    (b/block-hash block))))

(defn valid-parent-hash? [block chain]
  (= (get-in block [:header :parent-hash])
     (c/latest-block-hash chain)))
