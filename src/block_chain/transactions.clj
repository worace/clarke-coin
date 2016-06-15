(ns block-chain.transactions
  (:require [block-chain.utils :refer :all]
            [block-chain.wallet :as wallet]
            [block-chain.queries :as q]
            [clojure.set :refer [difference]]
            [block-chain.db :as db]
            [cheshire.core :as json]))

(def coinbase-reward 25)
(def input-signable (partial cat-keys [:source-hash :source-index]))
(def input-hashable (partial cat-keys [:source-hash :source-index]))
(def output-signable (partial cat-keys [:amount :address]))
(def output-hashable output-signable)

(defn txn-signable [txn]
  (apply str (concat (map input-signable (:inputs txn))
                     (map output-signable (:outputs txn))
                     [(:min-height txn) (:timestamp txn)])))

(defn txn-hashable [txn]
  (apply str (concat (map input-hashable (:inputs txn))
                     (map output-hashable (:outputs txn))
                     [(:min-height txn) (:timestamp txn)])))

(defn serialize-txn [txn]
  (write-json txn))

(defn read-txn [txn-json]
  (read-json txn-json))

(defn txn-hash [txn]
  (sha256 (txn-hashable txn)))

(defn hash-txn [txn]
  (assoc txn :hash (txn-hash txn)))

(defn tag-output [output index hash]
  "Adds an additional :coords key to the output containing
   its transaction-id (the txn hash) and index. This simplifies
   the process of working with outputs further down the pipeline
   as we don't have to have to refer back to the output's
   transaction context as frequently."
  (assoc output :coords
         {:transaction-id hash
          :index index}))

(defn tag-coords [txn]
  (let [tagged-outputs (map tag-output
                            (:outputs txn)
                            (range (count (:outputs txn)))
                            (repeat (:hash txn)))]
    (assoc txn :outputs (into [] tagged-outputs))))

(defn sign-txn
  "Takes a transaction map consisting of :inputs and :outputs, where each input contains
   a Source TXN Hash and Source Output Index. Signs each input by adding :signature
   which contains an RSA-SHA256 signature of the JSON representation of all the outputs in the transaction."
  [txn private-key]
  (let [signable (txn-signable txn)]
    (assoc txn
           :inputs
           (into [] (map (fn [i] (assoc i :signature (wallet/sign signable private-key)))
                         (:inputs txn))))))

(defn txn-fees
  "Finds available txn-fees from a pool of txns by finding the diff
   between cumulative inputs and cumulative outputs"
  [txns db]
  (let [sources (map (partial q/source-output db)
                     (mapcat :inputs txns))
        outputs (mapcat :outputs txns)]
    (- (reduce + (map :amount sources))
       (reduce + (map :amount outputs)))))

(defn coinbase
  "Generate new 'coinbase' mining reward transaction for the given
   address and txn pool. Coinbase includes no inputs and 1 output,
   where the address of the output is the address provided and the
   amount of the output is "
  ([db] (coinbase db (q/wallet-addr db)))
  ([db address]
   (tag-coords
    (hash-txn
     {:inputs []
      :min-height (q/chain-length db)
      :outputs [{:amount (+ coinbase-reward
                            (txn-fees (q/transaction-pool db) db))
                 :address address}]
      :timestamp (current-time-millis)}))))

(defn txns-for-next-block
  ([db coinbase-addr] (txns-for-next-block db
                                           coinbase-addr
                                           (q/transaction-pool db)))
  ([db coinbase-addr txn-pool] (into [(coinbase db coinbase-addr)]
                                     txn-pool)))

(defn raw-txn
  [amount address sources]
  (let [inputs (into [] (map (fn [s]
                               {:source-hash (get-in s [:coords :transaction-id])
                                :source-index (get-in s [:coords :index])})
                             sources))]
    {:inputs inputs
     :outputs [{:amount amount :address address}]
     :timestamp (current-time-millis)}))

(defn select-sources
  [amount output-pool]
  (let [avail (reduce + (map :amount output-pool))]
    (assert (>= avail amount) (str "Only found " avail " to fund " amount ".")))
  (let [greaters (filter #(>= (:amount %) amount) output-pool)
        lessers (filter #(< (:amount %) amount) output-pool)]
    (if (first greaters)
      (take 1 greaters)
      (loop [sources []
             pool lessers]
        (if (>= (reduce + (map :amount sources)) amount)
          sources
          (recur (conj sources (first pool))
                 (rest pool)))))))

(defn add-change [txn change-address sources total]
  (let [change (- (apply + (map :amount sources)) total)]
    (if (> change 0)
      (update-in txn
                 [:outputs]
                 conj
                 {:address change-address :amount change})
      txn)))

(defn unsigned-payment
  ([from-address to-address amount db] (unsigned-payment from-address to-address amount db 0))
  ([from-address to-address amount db fee]
   (let [output-pool (difference (into #{} (q/unspent-outputs from-address db))
                                 (q/outputs-spent-by-txn-pool db))
         sources (select-sources (+ amount fee) output-pool)
         txn (raw-txn amount to-address sources)]
     (-> txn
         (assoc :min-height (q/chain-length db))
         (add-change from-address sources (+ amount fee))
         (hash-txn)
         (tag-coords)))))

(defn payment
  "Generates a transaction to pay the specified amount to the
    specified address using provided key. Sources inputs from the
    unspent outputs available to the provided key. If a transaction
    fee is provided, it will be included in the value of inputs
    that are sourced, but not in the value of outputs that are
    spent. (i.e. the fee is the difference between input value
    and output value). Additionally, will roll any remaining
    value into an additional 'change' output back to the paying
    key."
  ([key address amount db] (payment key address amount db 0))
  ([key address amount db fee]
   (-> (unsigned-payment (:address key) address amount db fee)
       (sign-txn (:private key)))))

(defn transactions [blocks] (mapcat :transactions blocks))
(defn inputs [blocks] (mapcat :inputs (transactions blocks)))
(defn outputs [blocks] (mapcat :outputs (transactions blocks)))

(defn inputs-to-sources [inputs db]
  (zipmap inputs
          (map (partial q/source-output db) inputs)))

(defn consumes-output?
  [source-hash source-index input]
  (and (= source-hash (:source-hash input))
       (= source-index (:source-index input))))

(defn unspent?
  "Searches the chain to determine if the provided Transaction
   Output has already been referenced by another input."
  [blocks output]
  (let [inputs (inputs blocks)
        {:keys [transaction-id index]} (:coords output)
        spends-output? (partial consumes-output? transaction-id index)]
    (not-any? spends-output? inputs)))
