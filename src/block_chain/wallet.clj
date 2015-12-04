(ns block-chain.wallet
  (:require [clojure.java.io :as io]
            [clojure.string :refer [join split]])
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
  (.decode (java.util.Base64/getDecoder) str))

(defn encode64 [bytes]
  (.encodeToString (java.util.Base64/getEncoder) bytes))

(defn keydata [reader]
 (->> reader
      (PEMParser.)
      (.readObject)))

(defn pem-string->key-pair [string]
  (let [kd (keydata (io/reader (.getBytes string)))]
    (.getKeyPair (org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter.) kd)))

(defn pem-string->pub-key [string]
  (let [kd (keydata (io/reader (.getBytes string)))
        kf (KeyFactory/getInstance "RSA")
        spec (java.security.spec.X509EncodedKeySpec. (.getEncoded kd))]
    (.generatePublic kf spec)))

(defn pem-file->public-key [filepath]
  (pem-string->pub-key (slurp filepath)))

(defn pem-file->key-pair [filepath]
  (pem-string->private-key (slurp filepath)))

(defn format-pem-string [encoded key-type]
  (let [formatted (join "\n"
                        (map #(apply str %)
                             (partition 64 64 [] encoded)))]
    (str "-----BEGIN "
         key-type
         "-----\n"
         formatted
         "\n-----END "
         key-type
         "-----\n")))

(defn private-key->pem-string [key]
  (format-pem-string
   (encode64
    (.getEncoded
     (.toASN1Primitive
      (.parsePrivateKey
       (org.bouncycastle.asn1.pkcs.PrivateKeyInfo/getInstance (.getEncoded key))))))
   "RSA PRIVATE KEY"))

(defn public-key->pem-string [key]
  (format-pem-string (encode64 (.getEncoded key))
                     "PUBLIC KEY"))

(defn kp-generator []
  (doto (java.security.KeyPairGenerator/getInstance "RSA" "BC")
    (.initialize 2048)))

(defn generate-keypair []
  (.generateKeyPair (kp-generator)))

(defn encrypt [message public-key]
  (encode64
   (let [cipher (doto (javax.crypto.Cipher/getInstance "RSA/ECB/PKCS1Padding" "BC")
                  (.init javax.crypto.Cipher/ENCRYPT_MODE public-key))]
     (.doFinal cipher (.getBytes message)))))


(defn decrypt [message private-key]
  (apply str
         (map char (let [cipher (doto (javax.crypto.Cipher/getInstance "RSA/ECB/PKCS1Padding" "BC")
                                  (.init javax.crypto.Cipher/DECRYPT_MODE private-key))]
                     (.doFinal cipher (decode64 message))))))

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

;; (def keypair (generate-keypair))
;; (def private-key (read-pem-private-key "/Users/worace/Desktop/keys/private_key.pem"))
;; (def public-key (read-pem-public-key "/Users/worace/Desktop/keys/public_key.pem"))

#_(let [encrypted (encrypt "Pizza" public-key)]
  (println "decryted: " (decrypt encrypted (.getPrivate private-key))))

#_(def keypair (pem-file->private-key "./test/sample_private_key.pem"))

#_(encode64 (.getEncoded (.toASN1Primitive (.parsePrivateKey (org.bouncycastle.asn1.pkcs.PrivateKeyInfo/getInstance (.getEncoded (.getPrivate keypair)))))))

#_(let [k (.getPrivate (generate-keypair))
      w (java.io.StringWriter.)
      po (org.bouncycastle.openssl.jcajce.JcaMiscPEMGenerator.
          k)
      pem (org.bouncycastle.util.io.pem.PemWriter. w)]
  (.writeObject pem po)
  (.flush w)
  (.close w)
  (.toString w))

#_(.writeObject (org.bouncycastle.openssl.jcajce.JcaPEMWriter. (java.io.StringWriter.)) (.getPrivate (pem-file->private-key "./test/sample_private_key.pem")))


#_(let [priv (pem-file->private-key "./test/sample_private_key.pem")
      pub (pem-file->public-key "./test/sample_public_key.pem")
      enc (encrypt "pizza" pub)]
  (println enc)
  (println (decrypt enc (.getPrivate
                         priv))))

;;http://stackoverflow.com/questions/7611383/generating-rsa-keys-in-pkcs1-format-in-java
