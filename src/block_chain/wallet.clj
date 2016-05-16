(ns block-chain.wallet
  (:require [clojure.java.io :as io]
            [clojure.string :refer [join split]]
            [block-chain.key-serialization :as ks]
            [block-chain.encoding :refer :all]))

;; Thanks to http://nakkaya.com/2012/10/28/public-key-cryptography/
;; for many of these snippets

(java.security.Security/addProvider (org.bouncycastle.jce.provider.BouncyCastleProvider.))

(defn kp-generator [length]
  (doto (java.security.KeyPairGenerator/getInstance "RSA" "BC")
    (.initialize length)))

(defn key-map [kp]
  {:private (.getPrivate kp)
   :public (.getPublic kp)
   :address (ks/public-key->der-string (.getPublic kp))})

(defn generate-keypair
  "Generate an RSA Keypair. Accepts optional length. Default key length is 2048."
  ([] (generate-keypair 2048))
  ([length] (key-map (.generateKeyPair (kp-generator length)))))

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

(def wallet-path (str (System/getProperty "user.home") "/.wallet.der"))

(defn wallet-exists? [] (.exists (io/as-file wallet-path)))

(defn load-or-generate-keys!
  "Looks for wallet keypair to exist at ~/.wallet.der. If found, loads
   that keypair to use as our wallet. If not, generates a new keypair and
   saves it in that location for future use."
  []
   (if (wallet-exists?)
     (ks/der-file->key-pair wallet-path)
     (let [kp (generate-keypair)]
       (spit wallet-path
             (ks/private-key->der-string (:private kp)))
       kp)))

;; get keypair as map with:
;; {:private RSAPrivateKey :public RSAPublicKey :address "pub-key-der-string"}

(def keypair (load-or-generate-keys!))

(defn sign
  "RSA private key signing of a message. Takes message as string"
  [message private-key]
  (encode64
   (let [msg-data (.getBytes message)
         sig (doto (java.security.Signature/getInstance "SHA256withRSA" "BC")
               (.initSign private-key (java.security.SecureRandom.))
               (.update msg-data))]
     (.sign sig))))
