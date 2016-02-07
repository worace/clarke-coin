(ns block-chain.wallet
  (:require [clojure.java.io :as io]
            [clojure.string :refer [join split]]
            [block-chain.pem :as pem]
            [block-chain.transactions :as transactions]
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
   :public-pem (pem/public-key->pem-string (.getPublic kp))})

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

(def wallet-path (str (System/getProperty "user.home") "/.wallet.pem"))

(defn wallet-exists? [] (.exists (io/as-file wallet-path)))

(defn load-or-generate-keys!
  "Looks for wallet keypair to exist at ~/.wallet.pem. If found, loads
   that keypair to use as our wallet. If not, generates a new keypair and
   saves it in that location for future use."
  []
   (if (wallet-exists?)
     (key-map (pem/pem-file->key-pair wallet-path))
     (let [kp (generate-keypair)]
       (spit wallet-path
             (pem/private-key->pem-string (:private kp)))
       kp)))

;; get keypair as map with:
;; {:private "..." :public "..." :public-pem "..."}

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

(defn sign-txn
  "Takes a transaction map consisting of :inputs and :outputs, where each input contains
   a Source TXN Hash and Source Output Index. Signs each input by adding :signature
   which contains an RSA-SHA256 signature of the JSON representation of all the outputs in the transaction."
  [txn key]
  (let [signable (transactions/txn-signable txn)]
    (assoc txn
           :inputs
           (into [] (map (fn [i] (assoc i :signature (sign signable key)))
                         (:inputs txn))))))
