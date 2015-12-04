(ns block-chain.wallet
  (:require [clojure.java.io :as io]
            [clojure.string :refer [join split]]))

;; Thanks to http://nakkaya.com/2012/10/28/public-key-cryptography/
;; for many of these snippets

(java.security.Security/addProvider (org.bouncycastle.jce.provider.BouncyCastleProvider.))

(defn decode64 [str] (.decode (java.util.Base64/getDecoder) str))

(defn encode64 [bytes] (.encodeToString (java.util.Base64/getEncoder) bytes))

(defn keydata [reader]
 (->> reader
      (org.bouncycastle.openssl.PEMParser.)
      (.readObject)))

(defn pem-string->key-pair [string]
  "Convert a PEM-formatted private key string to a public/private keypair.
   Returns java.security.KeyPair."
  (let [kd (keydata (io/reader (.getBytes string)))]
    (.getKeyPair (org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter.) kd)))

(defn pem-string->pub-key [string]
  "Convert a PEM-formatted public key string to an RSA public key.
   Returns sun.security.rsa.RSAPublicKeyImpl"
  (let [kd (keydata (io/reader (.getBytes string)))
        kf (java.security.KeyFactory/getInstance "RSA")
        spec (java.security.spec.X509EncodedKeySpec. (.getEncoded kd))]
    (.generatePublic kf spec)))

(defn pem-file->public-key [filepath]
  "Read a file containing a PEM-formatted public key
   and return a matching key"
  (pem-string->pub-key (slurp filepath)))

(defn pem-file->key-pair [filepath]
  "Read a file containing a PEM-formatted private key and
   return a matching keypair"
  (pem-string->key-pair (slurp filepath)))

(defn format-pem-string [encoded key-type]
  "Takes a Base64-encoded string of key data and formats it
   for file-output following openssl's convention of wrapping lines
   at 64 characters and appending the appropriate header and footer for
   the specified key type"
  (let [chunked (->> encoded
                     (partition 64 64 [])
                     (map #(apply str %)))
        formatted (join "\n" chunked)]
    (str "-----BEGIN " key-type "-----\n"
         formatted
         "\n-----END " key-type "-----\n")))

(defn private-key->pem-string [key]
  "Convert RSA private keypair to a formatted PEM string for saving in
   a .pem file. By default these private keys will encode themselves as PKCS#8
   data (e.g. when calling (.getEncoded private-key)), so we have to convert it
   to ASN1, which PEM uses (this seems to also be referred to as PKCS#1).
   More info here http://stackoverflow.com/questions/7611383/generating-rsa-keys-in-pkcs1-format-in-java"
  (-> (.getEncoded key)
      (org.bouncycastle.asn1.pkcs.PrivateKeyInfo/getInstance)
      (.parsePrivateKey)
      (.toASN1Primitive)
      (.getEncoded)
      (encode64)
      (format-pem-string "RSA PRIVATE KEY")))

(defn public-key->pem-string [key]
  "Generate PEM-formatted string for a public key. This is simply a base64
   encoding of the key wrapped with the appropriate header and footer."
  (format-pem-string (encode64 (.getEncoded key))
                     "PUBLIC KEY"))

(defn kp-generator [length]
  (doto (java.security.KeyPairGenerator/getInstance "RSA" "BC")
    (.initialize length)))

(defn generate-keypair
  "Generate an RSA Keypair. Accepts optional length. Default key length is 2048."
  ([] (generate-keypair 2048))
  ([length] (.generateKeyPair (kp-generator length))))

(defn encrypt [message public-key]
  "Perform RSA public key encryption of the given message (as a string).
   Returns a Base64 encoded string of the encrypted data."
  (encode64
   (let [cipher (doto (javax.crypto.Cipher/getInstance "RSA/ECB/PKCS1Padding" "BC")
                  (.init javax.crypto.Cipher/ENCRYPT_MODE public-key))]
     (.doFinal cipher (.getBytes message)))))


(defn decrypt [message private-key]
  "RSA private key decryption of an encrypted message. Expects a base64-encoded string
   of the encrypted data."
  (apply str
         (map char (let [cipher (doto (javax.crypto.Cipher/getInstance "RSA/ECB/PKCS1Padding" "BC")
                                  (.init javax.crypto.Cipher/DECRYPT_MODE private-key))]
                     (.doFinal cipher (decode64 message))))))

(defn sign [message private-key]
  "RSA private key signing of a message. Takes message as string"
  (encode64
   (let [msg-data (.getBytes message)
         sig (doto (java.security.Signature/getInstance "SHA256withRSA" "BC")
               (.initSign private-key (java.security.SecureRandom.))
               (.update msg-data))]
     (.sign sig))))

(defn verify [encoded-sig message public-key]
  "RSA public key verification of a signature. Takes signature as base64-encoded string
   of signature data and message as a string. Returns true/false if signature
   is valid."
  (let [msg-data (.getBytes message)
        signature (decode64 encoded-sig)
        sig (doto (java.security.Signature/getInstance "SHA256withRSA" "BC")
              (.initVerify public-key)
              (.update msg-data))]
    (.verify sig signature)))

