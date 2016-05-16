(ns block-chain.transactions-test
  (:require [clojure.test :refer :all]
            [block-chain.transactions :refer :all]
            [block-chain.utils :refer :all]
            [block-chain.wallet :as wallet]
            [cheshire.core :as json]))

(def sample-transaction
  {:inputs [{:source-hash "1234"
             :source-index 1
             :signature "pizza"}]
   :outputs [{:amount 5
              :address "5678"}]})

(deftest test-hashable-transaction-string
  (let [hashable (str "1234" "1" "5" "5678")]
    (is (= hashable
         (txn-hashable sample-transaction)))))

(deftest test-hashes-transaction
  (let [hashable (str "1234" "1" "5" "5678")]
    (is (= (sha256 hashable)
         (txn-hash sample-transaction)))))

(deftest test-serializes-transaction
  (let [txn {:outputs [{:amount 5 :receiving-address "addr"}]
             :inputs [{:source-hash "1234" :source-index 0 :signature "sig"}]}]
    (is (= txn
           (read-txn (serialize-txn txn))))))

(deftest test-tagging-transaction-coordinates
  (let [tagged (tag-coords (hash-txn sample-transaction))]
    (is (= (:hash tagged) (get-in tagged [:outputs 0 :coords :transaction-id])))
    (is (= 0 (get-in tagged [:outputs 0 :coords :index])))))

(def unsigned-txn
  {:inputs [{:source-txn "9ed1515819dec61fd361d5fdabb57f41ecce1a5fe1fe263b98c0d6943b9b232e"
             :source-output-index 0}]
   :outputs [{:amount 5
              :receiving-address "addr"}]})

(def keypair (wallet/generate-keypair 512))
(deftest test-signs-transaction-inputs
  (is (= #{:source-txn :source-output-index}
         (into #{} (keys (first (:inputs unsigned-txn))))))
  (is (= #{:source-txn :source-output-index :signature}
         (into #{} (keys (first (:inputs (sign-txn unsigned-txn
                                                   (:private keypair)))))))))
