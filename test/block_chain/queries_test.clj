(ns block-chain.queries-test
  (:require [clojure.test :refer :all]
            [block-chain.db :as db]
            [block-chain.utils :refer :all]
            [block-chain.queries :refer :all]))


(defn fake-chain
  ([] (fake-chain db/genesis-block))
  ([{{hash :hash} :header :as block}]
   (lazy-seq (cons block
                   (-> block
                       (assoc-in [:header :parent-hash] hash)
                       (update-in [:header :hash] sha256)
                       (fake-chain))))))

(def sample-chain (take 5 (fake-chain)))
(def sample-db
  (reduce add-block db/initial-db (drop 1 sample-chain)))
(def sample-txn (-> (highest-block sample-db) :transactions first) )
(def utxo (-> sample-txn :outputs first))

(deftest test-highest-block
  (is (= (last sample-chain)) (highest-block sample-db)))

(deftest test-highest-hash (is (= (bhash (last sample-chain)) (highest-hash sample-db))))

(deftest test-longest-chain
  (is (= (list) (longest-chain db/empty-db)))
  (is (= (highest-hash sample-db)
         (bhash (first (longest-chain sample-db)))))
  (is (= (reverse (map bhash sample-chain)) (map bhash (longest-chain sample-db)))))

(deftest test-adding-block
  (let [updated (add-block db/empty-db db/genesis-block)]
    (is (= db/genesis-block (get-in updated [:blocks (bhash db/genesis-block)])))
    (is (= 1 (get-in updated [:chains (bhash db/genesis-block)])))
    (is (= 1 (count (:transactions updated))))
    (is (= (list (bhash db/genesis-block)) (get-in updated [:children (phash db/genesis-block)])))))

(deftest test-blocks-since
  (is (= 4 (count (blocks-since sample-db (bhash db/genesis-block)))))
  (is (= (map bhash (drop 1 (reverse (longest-chain sample-db))))
         (map bhash (blocks-since sample-db (bhash db/genesis-block))))))

(deftest test-fetching-txn
  (let [t (-> sample-db longest-chain last :transactions first)]
    (is (= t (get-txn sample-db (:hash t))))))

(deftest test-source-output
  (let [t (-> sample-db longest-chain last :transactions first)
        i {:source-hash (:hash t) :source-index 0 :signature "pizza"}]
    (is (= (-> t :outputs first)
           (source-output sample-db i)))))

(deftest test-utxos
  (is (= (->> sample-db
              :transactions
              vals
              (mapcat :outputs)
              (into #{}))
         (utxos sample-db))))

(def big-chain (take 100000 (fake-chain)))
(def big-db
  (reduce add-block db/initial-db (drop 1 big-chain)))

;; input: {source-hash "FFF.." source-index 0}
;; output {address "pizza" amount "50"}

;; UTXO:
;; Map of...? Coords? Vector of txn hash and index?
;; OR
;; Set of Coords
;; #{["txn-hash 1" 0] ["txn-hash 2" 0] }

(deftest test-output-assigned-to-key
  (is (assigned-to-key? (:address utxo) utxo))
  (is (not (assigned-to-key? "pizza" utxo))))
