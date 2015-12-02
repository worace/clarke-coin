(ns block-chain.wallet
  (:require [clojure.java.io :as io])
  (:import
    (org.bouncycastle.openpgp
     PGPUtil)
    (org.bouncycastle.openssl
     PEMParser)
    (java.security
     KeyFactory)
    (java.security.spec
     PKCS8EncodedKeySpec)))

;; Thanks to http://nakkaya.com/2012/10/28/public-key-cryptography/
;; for most of these snippets

(java.security.Security/addProvider
 (org.bouncycastle.jce.provider.BouncyCastleProvider.))

(defn decode64 [str]
  (javax.xml.bind.DatatypeConverter/parseBase64Binary str))

(defn keydata [filepath]
 (->> filepath
      (io/reader)
      (PEMParser.)
      (.readObject)))

(defn read-pem-private-key [filepath]
  (let [kd (keydata filepath)]
    (.getKeyPair (org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter.) kd)))

(defn read-pem-public-key [filepath]
  (let [kd (keydata filepath)
        kf (KeyFactory/getInstance "RSA")
        spec (java.security.spec.X509EncodedKeySpec. (.getEncoded kd))]
    (.generatePublic kf spec)))

(defn kp-generator []
  (doto (java.security.KeyPairGenerator/getInstance "RSA" "BC")
    (.initialize 2048)))

(defn generate-keypair []
  (.generateKeyPair (kp-generator)))

(defn encrypt [bytes public-key]
  (let [cipher (doto (javax.crypto.Cipher/getInstance "RSA/ECB/PKCS1Padding" "BC")
                 (.init javax.crypto.Cipher/ENCRYPT_MODE public-key))]
    (.doFinal cipher bytes)))


(defn decrypt [bytes private-key]
  (let [cipher (doto (javax.crypto.Cipher/getInstance "RSA/ECB/PKCS1Padding" "BC")
                 (.init javax.crypto.Cipher/DECRYPT_MODE private-key))]
    (.doFinal cipher bytes)))

(defn sign [data private-key]
  (let [sig (doto (java.security.Signature/getInstance "SHA1withRSA" "BC")
              (.initSign private-key (java.security.SecureRandom.))
              (.update data))]
    (.sign sig)))

(defn verify [signature data public-key]
  (let [sig (doto (java.security.Signature/getInstance "SHA1withRSA" "BC")
              (.initVerify public-key)
              (.update data))]
    (.verify sig signature)))

(def keypair (generate-keypair))
(def private-key (read-pem-private-key "/Users/worace/Desktop/keys/private_key.pem"))
(def public-key (read-pem-public-key "/Users/worace/Desktop/keys/public_key.pem"))

(let [encrypted (encrypt (.getBytes "Pizza") public-key)]
  (println "decryted: " (map char (decrypt encrypted (.getPrivate private-key)))))


