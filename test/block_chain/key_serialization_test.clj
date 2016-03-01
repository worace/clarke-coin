(ns block-chain.key-serialization-test
  (:require [clojure.test :refer :all]
            [block-chain.key-serialization :refer :all]))

(deftest test-reading-pem-keys
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

(deftest test-reading-der
  (let [der-string "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQqVq0QLLUg6OQM1VBuijPCc\nxml3NWMqGmtDhwHahFIGe/9Dx22f7x+jVDKXMXKJbvbchldhx+t0kcpFMHCs\ndJlfz0LhCu/M5mMFScBZht0TnYxoQnWOEoArgDrRyVU3nkexzAQHwWtH0x6P\nBh1SQOu4ZDZlQU2qOGV6gTj0dg+W8QIDAQAB\n"]
    (is (der-string->pub-key der-string))))

(deftest test-handles-non-wrapped-der
  (let [der-string "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQqVq0QLLUg6OQM1VBuijPCcxml3NWMqGmtDhwHahFIGe/9Dx22f7x+jVDKXMXKJbvbchldhx+t0kcpFMHCsdJlfz0LhCu/M5mMFScBZht0TnYxoQnWOEoArgDrRyVU3nkexzAQHwWtH0x6PBh1SQOu4ZDZlQU2qOGV6gTj0dg+W8QIDAQAB"]
    (is (der-string->pub-key der-string))))

(deftest test-reading-der-keys
  (let [pub-key-der (slurp "./test/sample_public_key.der")
        priv-key-der (slurp "./test/sample_private_key.der")]
    (is (= sun.security.rsa.RSAPublicKeyImpl (type (der-string->pub-key pub-key-der))))
    (is (= sun.security.rsa.RSAPublicKeyImpl
           (type (der-file->public-key "./test/sample_public_key.der"))))
    (is (= pub-key-der (public-key->der-string (der-string->pub-key pub-key-der))))
    (is (= #{:public :private}
           (into #{} (keys (der-file->key-pair "./test/sample_private_key.der")))))
    (is (= priv-key-der
           (private-key->der-string (:private (der-file->key-pair "./test/sample_private_key.der")))))))
