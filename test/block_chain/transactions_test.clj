(ns block-chain.transactions-test
  (:require [clojure.test :refer :all]
            [block-chain.transactions :refer :all]
            [cheshire.core :as json]
            [clojure.tools.namespace.repl :refer [refresh]]))




(def sample-pub-key-string (slurp "./test/sample_public_key.pem"))
(def sample-transaction
  [
   ;; transaction inputs
   [[
     ;; Hash of source txn
     "9ed1515819dec61fd361d5fdabb57f41ecce1a5fe1fe263b98c0d6943b9b232e"
     ;; index of source output within source txn
     0
     ;; Input Signature
     ;; RSA-Signed SHA256 hash of current transaction
     ;; Signed with priv key for pub key to which original output was assigned
     "psO/Bs7wt7xbq9VVLnykKp03fKKd4LAzTGnkXjpBhNSgXFt9tGF8f+5QusvRDjjds6NWiet4Bvs2cbfwG2IQfmuAMWwrycrmq8xCpNYnajK+Cyt9ogsU25Q65VYlciXWyrCAIUhtwCJ3Tlwyf1rHbJi6yV4qVHL+7SkxQexlIctlU4r4c0hmofnqcaYCpLfbQ0Kge6NJb7m2NaiWgXhRcJHFVmhQHUUYhxJeZq9PwLoL4nMKWrGKsUC31tRt/kz+ISROG033oG6LeKGozzGEehL8fMoESS9NEfSQtoGYZ2tvo3xqPSM+mQn852iPMtiBt1UldtiEkX6xdvNWdl3Tfg=="
     ]
    ]
   ;; transaction outputs
   [[
     ;; amount of coins to transfer
     5
     ;; pub key to which output will be assigned
     "-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAxpaKTGz1LlgVihe0dGlE\nPsn/cJk+Zo7uePr8hhjCAj+R0cxjE4Q8xKmVAA3YAxenoo6DShn8CSvR8AvNDgMm\nAdHvKjnZXsyPBBD+BNw5vIrEgQiuuBl7e0P8BfctGq2HHlBJ5i+1zitbmFe/Mnyr\nVRimxM7q7YGGOtqQ5ZEZRL1NcvS2sR+YxTL5YbCBXUW3FzLUjkmtSEH1bwWADCWj\nhz6IXWqYU0F5pRECVI+ybkdmirTbpZtQPyrND+iclsjnUUSONDLYm27dQnDvtiFc\nIn3PZ3Qxlk9JZ6F77+7OSEJMH3sB6/JcPZ0xd426U84SyYXLhggrBJMXCwUnzLN6\nuwIDAQAB\n-----END PUBLIC KEY-----\n"
     ]
    ]
   ])

(deftest test-serializes-transaction
  (is (= sample-transaction (json/parse-string (serialize sample-transaction)))))

(deftest test-hashes-transaction
  (is (= "7fc7ff0e187867a8820ae3e6561c9dd84bcf97e9c6b9c54a64a232546693d894"
         (txn-hash sample-transaction))))

(def unsigned-txn
  )

(deftest test-serializes-outputs
  (let [txn {:outputs [{:amount 5 :receiving-address "addr"}]}]
    (is (= (json/generate-string [[5 "addr"]])
           (serialize-outputs txn)))))

(run-tests)
