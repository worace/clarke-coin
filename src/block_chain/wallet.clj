(ns block-chain.wallet
  (:require [clj-pgp.core :as pgp]
            [clj-pgp.keyring :as keyring]
            [clojure.java.io :as io]
            [clj-pgp.generate :as pgp-gen])
  (:import
    (org.bouncycastle.openpgp
     PGPUtil)
    (org.bouncycastle.openssl
     PEMParser)
    (java.security
     KeyFactory)
    (java.security.spec
     PKCS8EncodedKeySpec)))


(java.security.Security/addProvider
 (org.bouncycastle.jce.provider.BouncyCastleProvider.))

(comment
 (def rsa (pgp-gen/rsa-keypair-generator 2048))
;;(def ec (pgp-gen/ec-keypair-generator "secp256k1"))

(defonce keypair (pgp-gen/generate-keypair rsa :rsa-general))
;;(defonce ecpair (pgp-gen/generate-keypair ec :ecdsa))

(def privkey (.getPrivateKey keypair))
;; -> org.bouncycastle.bcpg.RSASecretBCPGKey

(def pubkey (.getPublicKey keypair))
;; -> org.bouncycastle.bcpg.PublicKeyPacket

(def priv-encoded (pgp/encode-ascii privkey))

(def priv-sanitized
  (clojure.string/join
   (drop-last 2 (drop 3 (clojure.string/split priv-encoded #"\n")))))
 )

(def manual-fix
  "-----BEGIN PGP MESSAGE-----\nB/4oGhoha+1ctfwkpsFfzYA54rMlwH9CTNQO7nwu6D79/n/PJ9m1ISOPSh/Q8JLs\nmgPSH5Z+9NCtsSdRnDLQA+zXp9vHM/G6FQPdUnfQbX6nNr0jA75ms/UNs4K4fBZX\nYk/e1ACKY9npmUO0FMnH6vjq9GPPg4imwl8+g+I3+j5KwsmMzS9nKK9rhuSJTn6b\nE5JliT6mWT3WRycE2uARHmb+ST41gfwpPHN7kP5183ft/M2SVOpULq0gnwKGM6hz\ngzJoTC1tAKc8EUkQQr2OfhB5v2266G3w8Q/KzyASTTtd/4LQF4pEv6CcJSqs4JZ0\n8hs+gJ4IpGg2uLwlzLmcsRRpBACZB1Jyus9I9iJ72f4iBbH06GtpKmmv01HOpufx\n/MlRWq5SWg/oXEFGMV6grSpFSanQAHRm/uTbd7aegvnRWk/Pt0xGP2R17I09fgPz\npTJ+HMfwWzZUxP0/YQagAqnZz9DryOTXZDVFKrPqJFOCnkWMq+KhsE7Q6XRsS2c/\nbiMnPQQA4PjPA7eorrzRLLjg0dp281Vj7KqpRmoEC6zA96JvZPKKCAUAqcTUJM55\nwPzyfFubc2Zl6gIs+K4TDHM8jzgrNVAH1mbxPdIBbOESiJRB4RCKyqOfLzUwE7q1\nK/PIMF2HZ/oolMNTxAOY6jzjAuOH4sDTeS5nEqoh17sxZsjM0KsD/RT/EVnqdmjM\n9FrVaAqNUI9E4adAjid+Wk9sFtG9e+3I6n4P2jZbzdePlp0eq1x9U/n2uDp1piTf\nxAmG4ShL67oluohpqC+jcZQcTo529wkpcTRkMHx7TEvgeWx+B3+2P2hEFbQeIpwM\ndIhNDyCOmyH0p7VS22VQrpk0iCGxg7hh\n=i5dk\n-----END PGP MESSAGE-----\n")

(def secring
  (keyring/load-secret-keyring (io/file "/Users/worace/.gnupg/secring.gpg")))

(def seckey
  (keyring/get-secret-key
   secring
   (pgp/hex-id
    (first
     (keyring/list-public-keys secring)))))

(def josh-keystring
"MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDUF8UbI/KkxjbCSKuENECtbbIw
uUIe1pw933jw3J6eLYGwiFt9Q1eGBCne5aT9mDGp4gjdR4a+Y0PF/MCQLLqydpBN
DiMCAGQRkO64HN19wEQ8qrN8uWuVxz695MyKbJSNvOymqkLWULG56PC5arrqIk2V
qfc8zki8slb88HPu9QIDAQAB")

(defn decode64 [str]
  (javax.xml.bind.DatatypeConverter/parseBase64Binary str))

(def kf (KeyFactory/getInstance "RSA"))
(def josh-key
  (->> josh-keystring
       (decode64)
       (PKCS8EncodedKeySpec.)
       #_(.generatePublic kf)))
#_(byte-array (map (comp byte char) josh-keystring))

(def message
  "NdFBh8p8cKgAP0Cwj5o3riaEyh0TIMtn9h1w1eZtEZfpnyaggtfqhTe/p7QI\nah9cd87LD89zRRlkmpRqBI7+LXxWWr44IBFX4aQoeiYDIvxgUUZoZePaa9ak\nUF//MDQXaQmFR5jQBgXmn+zH9wYhKlTd0/p52Pqajazhb1vSTjURN0L63VKx\naDnuzi3rvc8ci+0khX4agmd6HROxHIPR3Ev6V3FPV7z+0xxivXjKAD8DPhub\n8s3b9TgN189iXQRlmvzj5KhDjV6oBZP6ZIUW3ckjBd0bfYg+nivt3KcvZl0E\nxUNN7awgF4hYn41kKPrZfgzXkhzm4SeuKxMYM57kyg==")
    ;; PKCS8EncodedKeySpec spec =
    ;;   new PKCS8EncodedKeySpec(keyBytes);
    ;; KeyFactory kf = KeyFactory.getInstance("RSA");
    ;; return kf.generatePrivate(spec);

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


