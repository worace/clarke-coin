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

(deftest hashes-block-by-hashing-header-values
  (let [b {:header {:parent-hash "0"
                    :transactions-hash "1"
                    :target "2"
                    :nonce 3}}]
    (is (= (sha256 "0123") (block-hash b)))))

(deftest determines-if-block-meets-specified-target
  (let [b1 {:header {:hash "0F" :target "FF"}}
        b2 {:header {:hash "FF" :target "0F"}}]
    (is (meets-target? b1))
    (is (not (meets-target? b2)))))

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

(deftest getting-average-spacing-of-list
  (is (= 4 (avg-spacing [0 4 8 12])))
  (is (= 3 (avg-spacing [0 4 7 9]))))

(deftest adjusting-target
  (let [blocks [{:header {:timestamp 0 :target (hex-string 100)}}
                {:header {:timestamp 60 :target (hex-string 100)}}]]
    (is (= (hex-string 100) (adjusted-target blocks 60))))
  (let [blocks [{:header {:timestamp 0 :target (hex-string 100)}}
                {:header {:timestamp 30 :target (hex-string 100)}}
                {:header {:timestamp 60 :target (hex-string 100)}}]]
    ;; blocks are spacing 30 s against desired freq of 60, so we should halve the target
    (is (= (hex-string 50) (adjusted-target blocks 60))))
  (let [blocks [{:header {:timestamp 0 :target (hex-string 100)}}
                {:header {:timestamp 120 :target (hex-string 100)}}
                {:header {:timestamp 240 :target (hex-string 100)}}]]
    ;; blocks are spacing 120 s against desired freq of 60, so we should double the target
    (is (= (hex-string 200) (adjusted-target blocks 60)))))

(run-tests)
