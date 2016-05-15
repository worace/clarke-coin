(ns block-chain.chain-test
  (:require [clojure.test :refer :all]
            [block-chain.chain :refer :all]
            [block-chain.utils :refer :all]
            [block-chain.db :as db]
            [block-chain.wallet :as wallet]
            [block-chain.miner :as miner]
            [block-chain.queries :as q]
            [clojure.tools.namespace.repl :refer [refresh]]))

(def test-db (atom db/empty-db))
(miner/mine-and-commit-db test-db)

(def sample-txn (-> (q/highest-block @test-db) :transactions first) )

(def utxo (-> sample-txn
              :outputs
              first))

(deftest test-output-assigned-to-key
  (is (assigned-to-key? (:address utxo) utxo))
  (is (not (assigned-to-key? "pizza" utxo))))

(deftest find-txn-by-hash
  (is (= sample-txn
         (txn-by-hash (:hash sample-txn)
                      (q/longest-chain @test-db)))))

#_(deftest tells-if-txo-is-unspent
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
;; (deftest test-finding-output-coords-for-b
;;   (is (= [{:source-hash "e6f4ed3ff30f3936d99385d33f6410c22781359e3cfe69ccabcad109ee9ab40f" :source-index 0}
;;           {:source-hash "5771a4321e21bc55d236602aecadd53243fe49cd9f9041383b282be8c0fe8559" :source-index 0}]
;;          (unspent-output-coords key-b chain))))

;; (deftest test-output-coords-for-a
;;   ;; 3x coinbase and 1x transfer from key b
;;   (is (= [{:source-hash "982436813783205f5d01cfd4bdff2c93f1bf3729690d5c825fb802d4bd6f9b11" :source-index 0}
;;           {:source-hash "9c2330d27b041b9d4b6204e36a2faf5112a24463dac064f790aff8870e18650b" :source-index 0}
;;           {:source-hash "f3d85daa66d3e3e70606c1bbb7f8f4294eeb5d496aab1f9d26672048358f00ff" :source-index 0}
;;           {:source-hash "72f1f4e69cf9d7700700885b7845e54cbab7179a83811fe0898a2246aa6c9278" :source-index 0}]
;;          (unspent-output-coords key-a chain))))

;; (deftest test-finding-source-of-input
;;   (let [txn (first (:transactions (first chain)))
;;         output (first (:outputs txn))
;;         input {:source-hash (:hash txn) :source-index 0}]
;;     (is (= output (source-output chain input)))
;;     (is (nil? (source-output [] input)))))




