(ns block-chain.key-serialization
  (:require [clojure.java.io :as io]
            [clojure.string :refer [join split]]
            [block-chain.encoding :refer :all]))

(defn der-string->pub-key [string]
  (let [non-wrapped (clojure.string/replace string #"\n" "")
        key-bytes (decode64 non-wrapped)
        spec (java.security.spec.X509EncodedKeySpec. key-bytes)
        key-factory (java.security.KeyFactory/getInstance "RSA")]
    (.generatePublic key-factory spec)))

(defn der-file->public-key [filepath]
  (der-string->pub-key (slurp filepath)))

(defn public-key->der-string [key]
  "Generate DER-formatted string for a public key."
  (clojure.string/replace (encode64 (.getEncoded key))
                          #"\n"
                          ""))

(defn der-string->private-key [string]
  (.generatePrivate (java.security.KeyFactory/getInstance "RSA")
                    (java.security.spec.PKCS8EncodedKeySpec.
                     (decode64 (.getBytes string)))))

(defn private-key->public-key [private]
  (let [modulus (.getModulus private)
        exponent (.getPublicExponent private)
        public-spec (java.security.spec.RSAPublicKeySpec. modulus exponent)
        kf (java.security.KeyFactory/getInstance "RSA")]
    (.generatePublic kf public-spec)))

(defn der-string->key-pair [string]
  (let [private (der-string->private-key string)
        public (private-key->public-key private)]
    {:private private :public public :address (public-key->der-string public)}))

(defn der-file->key-pair [filepath]
  (der-string->key-pair (slurp filepath)))

(defn private-key->der-string [private-key]
  (encode64 (.getEncoded private-key)))
