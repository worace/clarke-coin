(ns block-chain.queries-test
  (:require [clojure.test :refer :all]
            [block-chain.db :as db]
            [block-chain.db-keys :as db-key]
            [block-chain.test-helper :as th]
            [clj-leveldb :as ldb]
            [block-chain.utils :refer :all]
            [block-chain.queries :refer :all]))

(defn fake-next-block [{{hash :hash} :header :as block}]
  (-> block
      (assoc-in [:header :parent-hash] hash)
      (update-in [:header :hash] sha256)))

(defn fake-chain
  ([] (fake-chain db/genesis-block))
  ([{{hash :hash} :header :as block}]
   (lazy-seq (cons block
                   (-> block
                       fake-next-block
                       (fake-chain))))))

(def sample-chain (take 5 (fake-chain)))
(def empty-db (atom nil))
(def sample-db (atom nil))

(defn setup [tests]
  (with-open [empty-conn (th/temp-db-conn)
              sample-conn (th/temp-db-conn)]
    (reset! empty-db (db/db-map empty-conn))
    (reset! sample-db (db/db-map sample-conn))
    (doseq [b sample-chain] (add-block! sample-db b))
    (tests)))

(use-fixtures :each setup)

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
    (is (= 1 (chain-length updated (bhash db/genesis-block))))
    (is (= 1 (count (all-txns updated))))
    (is (= #{(bhash db/genesis-block)}
           (children updated (phash db/genesis-block))))))

(deftest test-adding-child-twice-doesnt-duplicate-in-child-listing
  (let [parent (first sample-chain)
        child (second sample-chain)]
    (is (= #{(bhash child)} (children @sample-db (bhash parent))))
    (add-block! sample-db child)
    (is (= #{(bhash child)} (children @sample-db (bhash parent))))))

(deftest test-adding-block-clears-its-txns-from-pool
  (let [next (fake-next-block (highest-block @sample-db))]
    (add-transaction-to-pool! sample-db
                              (first (:transactions next)))
    (is (= 1 (count (transaction-pool @sample-db))))
    (add-block! sample-db next)
    (is (empty? (transaction-pool @sample-db)))))

(deftest test-adding-block-clears-txns-with-overlapping-inputs-from
  (let [txn1 {:inputs [{:source-hash "pizza"
                        :source-index "0"
                        :signature "sig1234"}]
              :outputs [{:address "1234" :amount 10}]}
        txn2 {:inputs [{:source-hash "pizza"
                        :source-index "0"
                        :signature "differentsig"}]
              :outputs [{:address "diffaddr" :amount 10}]}
        next (update (fake-next-block (highest-block @sample-db))
                     :transactions
                     conj
                     txn1)]
    (add-transaction-to-pool! sample-db txn2)
    (is (= 1 (count (transaction-pool @sample-db))))
    (add-block! sample-db next)
    (is (empty? (transaction-pool @sample-db)))))

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

;; TODO this is important enough to warrant more testing
(deftest test-utxos
  (is (= (->> @sample-db
              all-txns
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
  (let [utxo (utxo @sample-db)]
    (is (assigned-to-key? (:address utxo) utxo))
    (is (not (assigned-to-key? "pizza" utxo)))))

(deftest test-adding-utxos
  (let [txn (get-in db/genesis-block [:transactions 0])
        address (get-in txn [:outputs 0 :address])
        key-hash (sha256 address)
        txn-id (:hash txn)
        utxo-key (str "utxos:" key-hash ":" txn-id ":" "0")
        conn (:block-db @empty-db)]
    (is (nil? (ldb/get conn utxo-key)))
    (add-block! empty-db db/genesis-block)
    (is (not (nil? (ldb/get conn utxo-key))))
    (is (= (first (:outputs txn))
           (ldb/get conn utxo-key)))
    (is (= 25 (utxo-balance @empty-db address)))))

(def simple-block
  {:header {:parent-hash "0000"
            :hash "block-1"}
   :transactions [{:hash "txn-1"
                   :inputs []
                   :outputs [{:amount 25
                              :address "addr-a"}]}]})

(def next-block
  {:header {:parent-hash "block-1"
            :hash "block-2"}
   :transactions [{:hash "txn-2"
                   :inputs [{:source-hash "txn-1" :source-index 0}]
                   :outputs [{:amount 25 :address "addr-b"}]}]})
(def fork-block-1
  {:header {:parent-hash "block-1"
            :hash "fork-1"}
   :transactions [{:hash "fork-txn-1"
                   :inputs []
                   :outputs [{:amount 25
                              :address "addr-a"}]}]})
(def fork-block-2
  {:header {:parent-hash "fork-1"
            :hash "fork-2"}
   :transactions [{:hash "fork-txn-2"
                   :inputs []
                   :outputs [{:amount 25
                              :address "addr-a"}]}]})

(deftest removing-spent-utxos
  (add-block! empty-db simple-block)
  (is (= 25 (utxo-balance @empty-db "addr-a")))
  (add-block! empty-db next-block)
  (is (= {:amount 25 :address "addr-a"} (source-output @empty-db {:source-hash "txn-1" :source-index 0})))
  (is (= 0 (utxo-balance @empty-db "addr-a")))
  (is (= 25 (utxo-balance @empty-db "addr-b"))))

(deftest utxo-balance-for-nonexistent-key
  (is (= 0 (utxo-balance @empty-db "pizza"))))

(deftest tracking-fork-paths
  (add-block! empty-db simple-block)
  (add-block! empty-db next-block)
  (add-block! empty-db fork-block-1)
  (add-block! empty-db fork-block-2)

  (is (= ["fork-1" "fork-2"] (fork-path @empty-db "fork-2" "block-2")))
  (is (= [] (fork-path @empty-db "block-2" "block-2")))
  (is (= ["fork-1"] (fork-path @empty-db "fork-1" "block-2")))
  )

#_(deftest utxo-rewinding-for-fork-resolution
  (add-block! empty-db simple-block)
  (is (= 25 (utxo-balance @empty-db "addr-a")))
  (add-block! empty-db next-block)
  (is (= 0 (utxo-balance @empty-db "addr-a")))
  (is (= 25 (utxo-balance @empty-db "addr-b")))

  (add-block! empty-db fork-block-1)
  (is (= 0 (utxo-balance @empty-db "addr-a")))
  (is (= 25 (utxo-balance @empty-db "addr-b")))

  (add-block! empty-db fork-block-2)
  (is (= 75 (utxo-balance @empty-db "addr-a")))
  (is (= 0 (utxo-balance @empty-db "addr-b")))

  )

(deftest test-building-db-changesets
  (let [b db/genesis-block
        cs (changeset-add-block @empty-db b)]
    (is (= #{:put :delete} (into #{} (keys cs))))
    (is (contains? (:put cs)
                   [(db-key/block (bhash b)) b]))
    (is (contains? (:put cs)
                   [(db-key/child-blocks (phash b)) #{(bhash b)}]))
    (is (contains? (:put cs)
                   [(db-key/chain-length (bhash b)) 1]))
    (is (contains? (:put cs)
                   [db-key/highest-hash (bhash b)]))))

(run-tests)
