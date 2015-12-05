(ns block-chain.wallet
  (:require [clojure.java.io :as io]
            [clojure.string :refer [join split]]
            [block-chain.base64 :refer :all]))

;; Thanks to http://nakkaya.com/2012/10/28/public-key-cryptography/
;; for many of these snippets

(java.security.Security/addProvider (org.bouncycastle.jce.provider.BouncyCastleProvider.))

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


(defn sign-txn [txn])
