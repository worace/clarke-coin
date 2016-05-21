(ns block-chain.schemas
  (:require [schema.core :as s]))

(s/defschema UnsignedTransactionInput {:source-hash s/Str :source-index s/Int})
(s/defschema SignedTransactionInput (assoc UnsignedTransactionInput :signature s/Str))
(s/defschema TransactionOutput {:amount s/Int
                                :address s/Str
                                (s/optional-key :coords) {:transaction-id s/Str
                                                          :index s/Int}})
(s/defschema UnsignedTransaction
  {:hash s/Str
   :min-height s/Int
   :timestamp s/Int
   :inputs [UnsignedTransactionInput]
   :outputs [TransactionOutput]})

(s/defschema Transaction
  (assoc UnsignedTransaction :inputs [SignedTransactionInput]))

(s/defschema Block
  {:header {:parent-hash s/Str
            :hash s/Str
            :transactions-hash s/Str
            :target s/Str
            :timestamp s/Int
            :nonce s/Int}
   :transactions [Transaction]})

(s/defschema Peer {:host s/Str :port s/Str})
