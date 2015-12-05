(ns block-chain.wallet-test
  (:require [clojure.test :refer :all]
            [block-chain.wallet :refer :all]
            [block-chain.pem :refer [pem-file->key-pair
                                     pem-file->public-key
                                     private-key->pem-string
                                     pem-string->key-pair]]
            [clojure.tools.namespace.repl :refer [refresh]]))

(deftest test-encrypt-and-decrypt-with-fresh-keys
  (let [kp (generate-keypair)
        pub (.getPublic kp)
        priv (.getPrivate kp)
        ]
    (is (= "pizza"
           (decrypt (encrypt "pizza" pub)
                    priv)))))

(deftest test-encrypt-and-decrypt-with-deserialized-keys
  (let [priv (.getPrivate (pem-file->key-pair "./test/sample_private_key.pem"))
        pub (pem-file->public-key "./test/sample_public_key.pem")]
    (is (= "pizza"
           (decrypt (encrypt "pizza" pub)
                    priv)))))

(deftest test-serialize-and-deserialize-new-key
  (let [kp (generate-keypair)
        encrypted (encrypt "pizza" (.getPublic kp))
        pem-str (private-key->pem-string (.getPrivate kp))
        deserialized (pem-string->key-pair pem-str)]
    (is (= "pizza"
           (decrypt encrypted
                    (.getPrivate deserialized))))))

(deftest test-signing-and-verifying
  (let [priv (.getPrivate (pem-file->key-pair "./test/sample_private_key.pem"))
        pub (pem-file->public-key "./test/sample_public_key.pem")
        sig (sign "pizza" priv)]
    (is (verify sig "pizza" pub))
    (is (not (verify sig "lul" pub)))))
