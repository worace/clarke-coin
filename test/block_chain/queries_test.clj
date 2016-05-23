(ns block-chain.queries-test
  (:require [clojure.test :refer :all]
            [block-chain.db :as db]
            [block-chain.test-helper :as th]
            [clj-leveldb :as ldb]
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
(def empty-path (str "/tmp/" (th/dashed-ns-name) "-empty"))
(def sample-path (str "/tmp/" (th/dashed-ns-name) "-sample"))
(def empty-db (atom nil))
(def sample-db (atom nil))

(defn setup [tests]
  (ldb/destroy-db empty-path)
  (ldb/destroy-db sample-path)
  (reset! empty-db (db/make-db empty-path))
  (reset! sample-db (db/make-db sample-path))
  (doseq [b sample-chain] (add-block! sample-db b))
  (try
    (tests)
    (finally
      (th/close-conns! @empty-db @sample-db))))

(use-fixtures :once setup)

(defn sample-txn [db] (-> (highest-block db) :transactions first) )
(defn utxo [db] (-> (sample-txn db) :outputs first))

(deftest test-highest-block
  (is (= (last sample-chain)
         (highest-block @sample-db))))

(deftest test-highest-hash (is (= (bhash (last sample-chain))
                                  (highest-hash @sample-db))))

(deftest test-longest-chain
  (is (= (list) (longest-chain @empty-db)))
  (is (= (highest-hash @sample-db)
         (bhash (first (longest-chain @sample-db)))))
  (is (= (reverse (map bhash sample-chain))
         (map bhash (longest-chain @sample-db)))))

(deftest test-adding-block
  (let [updated (add-block @empty-db db/genesis-block)]
    (is (= db/genesis-block (get-block updated (bhash db/genesis-block))))
    (is (= 1 (get-in updated [:chains (bhash db/genesis-block)])))
    (is (= 1 (count (:transactions updated))))
    (is (= (list (bhash db/genesis-block))
           (children updated (phash db/genesis-block))))))

(deftest test-blocks-since
  (is (= 4 (count (blocks-since @sample-db (bhash db/genesis-block)))))
  (is (= (map bhash (drop 1 (reverse (longest-chain @sample-db))))
         (map bhash (blocks-since @sample-db (bhash db/genesis-block))))))

(deftest test-fetching-txn
  (let [t (-> @sample-db longest-chain last :transactions first)]
    (is (= t (get-txn @sample-db (:hash t))))))

(deftest test-source-output
  (let [t (-> @sample-db longest-chain last :transactions first)
        i {:source-hash (:hash t) :source-index 0 :signature "pizza"}]
    (is (= (-> t :outputs first)
           (source-output @sample-db i)))))

(deftest test-utxos
  (is (= (->> @sample-db
              :transactions
              vals
              (mapcat :outputs)
              (into #{}))
         (utxos @sample-db))))

;; ;; input: {source-hash "FFF.." source-index 0}
;; ;; output {address "pizza" amount "50"}
;; ;; UTXO:
;; ;; Map of...? Coords? Vector of txn hash and index?
;; ;; OR
;; ;; Set of Coords
;; ;; #{["txn-hash 1" 0] ["txn-hash 2" 0] }

(deftest test-output-assigned-to-key
  (let [utxo (utxo (sample-txn @sample-db))]
    (is (assigned-to-key? (:address utxo) utxo))
    (is (not (assigned-to-key? "pizza" utxo)))))
