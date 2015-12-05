(ns block-chain.pem-test
  (:require [clojure.test :refer :all]
            [block-chain.pem :refer :all]))

(deftest test-reading-keys
  (let [pub-key-pem (slurp "./test/sample_public_key.pem")
        priv-key-pem (slurp "./test/sample_private_key.pem")]
    (is (= sun.security.rsa.RSAPublicKeyImpl (type (pem-string->pub-key pub-key-pem))))
    (is (= sun.security.rsa.RSAPublicKeyImpl
           (type (pem-file->public-key "./test/sample_public_key.pem"))))
    (is (= pub-key-pem (public-key->pem-string (pem-string->pub-key pub-key-pem))))
    (is (= java.security.KeyPair
           (type (pem-file->key-pair "./test/sample_private_key.pem"))))
    (is (= priv-key-pem
           (private-key->pem-string (.getPrivate (pem-file->key-pair "./test/sample_private_key.pem")))))))
