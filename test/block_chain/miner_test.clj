(ns block-chain.miner-test
  (:require  [clojure.test :refer :all]
             [pandect.algo.sha256 :refer [sha256]]
             [block-chain.utils :refer :all]
             [clojure.math.numeric-tower :as math]
             [block-chain.miner :refer :all]
             ))

(def sample-block
  {:header {:parent-hash "0"
            :transactions-hash "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
            :target "F000000000000000000000000000000000000000000000000000000000000000", :timestamp 1450057326
            :nonce 0
            }
   :transactions []})

(def max-sha2 (apply str (take 64 (repeat "F"))))

(deftest returns-valid-block-with-same-nonce
  (let [b {:header {:target max-sha2 :nonce 0}}
        mined (mine b)]
    (is (= b (update-in mined [:header] dissoc :hash)))))

(def large-target (str (format "%x" (biginteger(math/expt 2 254)))))

(deftest mines-block-by-updating-nonce-until-hash-is-valid
  (let [b {:header {:target large-target :nonce 0}}
        mined (mine b)]
    (is (= 1 (get-in mined [:header :nonce])))))

(run-tests)
