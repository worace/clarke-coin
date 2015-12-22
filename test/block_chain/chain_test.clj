(ns block-chain.chain-test
  (:require [clojure.test :refer :all]
            [block-chain.chain :refer :all]
            [block-chain.utils :refer :all]
            [clojure.tools.namespace.repl :refer [refresh]]))

(def chain (read-stored-chain "./test/sample_chain.json"))
(def utxo (get-in chain [0 :transactions 0 :outputs 0]))
(def key-a "-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAn04rVGD/selxmPcYRmjc\nHE19e5XQOueBekYEdQHD5q06mzuLQqErjJDANt80JjF6Y69dOU23cqlZ1B/2Pj48\nK+OROFBlrT5usrAJa6we0Ku33w6avl47PXanhcfi39GKNr8RadCKHoG1klvKqVEm\nuhJO/2foXAb6LATB0YoQuH8sDvUmLHSSPTTCBO2YGtsCvlMBNxdnvGVyZA5iIPwu\nw7DN00jG8RJn0KQRDgTM+nFNxcw9bIOrfSxOmNTDo1y8EFwFiYZ6rORLN+cNL50T\nU1Kl/ShX0dfvXauSjliVSl3sll1brVC500HYlAK61ox5BakdZG6R+3tJKe1RAs3P\nNQIDAQAB\n-----END PUBLIC KEY-----\n")
(def key-b "-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAuFl76216Veu5/H2MM4lO\nNFOuZLGcwxeUQzdmW2g+da5mmjyV3RiuYueDJFlAgx2iDASQM+rK1qKp7lj352DU\n3gABqJ5Tk1mRvGHTGz+aP4sj8CKUnjJIQVmmleiRZ47wRDsnrg9N0XyfW+aiPKxl\njvr1pkKJmryO+u2d69Tc69bNsqpGzFLTdO3w1k/jxa0pUAQNqf11MJSrzF7u/Z+8\nmaqFZlzZ5o1LgqTLMpeFg0pcMIKuZb9yQ1IKqOjLsvTvYYyBbNU31FD8qVY/R64z\nbrIYbfWXNiUrYOXyIq7rqegLf3fx+aJGgwUOGYr2MJjY+ZR5Z+cIKJiAgNnpkBWR\nhwIDAQAB\n-----END PUBLIC KEY-----\n")

(def sample-block
  {:header {:parent-hash "0"
            :transactions-hash "tx_hash"
            :target "F000000000000000000000000000000000000000000000000000000000000000"
            :timestamp 1450057326
            :nonce 0
            }
   :transactions []
   :hash "some-hash"})

(deftest test-reads-stored-chain
  (is (vector? (read-stored-chain))))

(deftest adds-to-chain
  (let [c (count @block-chain)]
    (add-block! {})
    (is (= (inc c) (count @block-chain)))))

(deftest test-reads-blocks-from-json-file
  (is (vector? chain))
  (is (= 5 (count chain)))
  (is (= "acc2a45c839b7f7f25349442c68de523894f32897dea1f62fd4a2c1921d785a8"
         (get-in chain [0 :header :transactions-hash]))))

(deftest get-block-by-hash
  (let [b (first chain)
        found (block-by-hash
                (get-in b [:header :hash])
                chain)]
    (is (= b found))))

(deftest test-output-assigned-to-key
  (is (assigned-to-key? utxo (:address utxo)))
  (is (not (assigned-to-key? utxo "pizza"))))

(deftest find-txn-by-hash
  (is (txn-by-hash "e6f4ed3ff30f3936d99385d33f6410c22781359e3cfe69ccabcad109ee9ab40f"
                   chain)))

;; 5th block contains transaction spending the coinbase
;; output of txn "e6f4ed3ff30f3936d99385d33f6410c22781359e3cfe69ccabcad109ee9ab40f"
(def spent-coords
  {:source-hash "e6f4ed3ff30f3936d99385d33f6410c22781359e3cfe69ccabcad109ee9ab40f"
   :source-index 0})

(deftest tells-if-txo-is-unspent
  (let [txn-hash (get-in chain [0 :transactions :hash])]
    (is (unspent? txn-hash 0 chain))
    (is (consumes-output? (:source-hash spent-coords)
                          0
                          (get-in chain [4 :transactions 1 :inputs 0])))
    (is (not (unspent? (:source-hash spent-coords)
                       0
                       chain)))))

;; chain contains 5 blocks with txns to 2 keys
;; key A
;; "-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAuFl76216Veu5/H2MM4lO\nNFOuZLGcwxeUQzdmW2g+da5mmjyV3RiuYueDJFlAgx2iDASQM+rK1qKp7lj352DU\n3gABqJ5Tk1mRvGHTGz+aP4sj8CKUnjJIQVmmleiRZ47wRDsnrg9N0XyfW+aiPKxl\njvr1pkKJmryO+u2d69Tc69bNsqpGzFLTdO3w1k/jxa0pUAQNqf11MJSrzF7u/Z+8\nmaqFZlzZ5o1LgqTLMpeFg0pcMIKuZb9yQ1IKqOjLsvTvYYyBbNU31FD8qVY/R64z\nbrIYbfWXNiUrYOXyIq7rqegLf3fx+aJGgwUOGYr2MJjY+ZR5Z+cIKJiAgNnpkBWR\nhwIDAQAB\n-----END PUBLIC KEY-----\n"
;; has 3 coinbases and 1 txo transferred from other key
;; key B
;; "-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAn04rVGD/selxmPcYRmjc\nHE19e5XQOueBekYEdQHD5q06mzuLQqErjJDANt80JjF6Y69dOU23cqlZ1B/2Pj48\nK+OROFBlrT5usrAJa6we0Ku33w6avl47PXanhcfi39GKNr8RadCKHoG1klvKqVEm\nuhJO/2foXAb6LATB0YoQuH8sDvUmLHSSPTTCBO2YGtsCvlMBNxdnvGVyZA5iIPwu\nw7DN00jG8RJn0KQRDgTM+nFNxcw9bIOrfSxOmNTDo1y8EFwFiYZ6rORLN+cNL50T\nU1Kl/ShX0dfvXauSjliVSl3sll1brVC500HYlAK61ox5BakdZG6R+3tJKe1RAs3P\nNQIDAQAB\n-----END PUBLIC KEY-----\n"
;; has 2 coinbases
(deftest test-finding-outputs-for-key
  (is (= 6 (count (outputs chain))))
  (is (= 4 (count (key-outputs key-a chain))))
  (is (= 2 (count (key-outputs key-b chain)))))

(deftest test-finding-unspent-outputs-for-key)

