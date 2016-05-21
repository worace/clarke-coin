(ns block-chain.wallet-test
  (:require [clojure.test :refer :all]
            [block-chain.wallet :refer :all]
            [block-chain.key-serialization :as ks]))

(deftest test-encrypt-and-decrypt-with-fresh-keys
  (let [kp (generate-keypair 128)
        pub (:public kp)
        priv (:private kp)]
    (is (= "pizza"
           (decrypt (encrypt "pizza" pub)
                    priv)))))

(deftest test-encrypt-and-decrypt-with-deserialized-keys
  (let [priv (:private (ks/der-file->key-pair "./test/sample_private_key.der"))
        pub (ks/der-file->public-key "./test/sample_public_key.der")]
    (is (= "pizza"
           (decrypt (encrypt "pizza" pub)
                    priv)))))

(deftest test-serialize-and-deserialize-new-key
  (let [kp (generate-keypair 128)
        encrypted (encrypt "pizza" (:public kp))
        der-str (ks/private-key->der-string (:private kp))
        deserialized (ks/der-string->key-pair der-str)]
    (is (= "pizza"
           (decrypt encrypted
                    (:private deserialized))))))

(deftest test-signing-and-verifying
  (let [priv (:private (ks/der-file->key-pair "./test/sample_private_key.der"))
        pub (ks/der-file->public-key "./test/sample_public_key.der")
        sig (sign "pizza" priv)]
    (is (verify sig "pizza" pub))
    (is (not (verify sig "lul" pub)))))


(deftest test-serializing-and-deserializing-der-keys
  (let [kp (generate-keypair 512)
        sig (sign "pizza" (:private kp))
        private-der (ks/private-key->der-string (:private kp))
        public-der (ks/public-key->der-string (:public kp))]
    (is (verify sig "pizza" (:public kp)))
    (is (verify sig "pizza" (ks/der-string->pub-key public-der)))
    (is (verify sig "pizza" (:public (ks/der-string->key-pair private-der))))))
