(ns block-chain.ecdsa
  (:import [java.security KeyFactory]
           [java.security.spec X509EncodedKeySpec]
           [java.security.spec PKCS8EncodedKeySpec]
           [org.bouncycastle.jce ECNamedCurveTable]
           [org.bouncycastle.jce.spec ECParameterSpec])
  (:require [clj-pgp.core :as pgp]
            [block-chain.encoding :refer :all]
            [clj-pgp.signature :as pgp-sig]
            [byte-streams :as bytes]
            [clj-pgp.generate :as pgp-gen]))

;; CLJ PGP
(def ec-gen (pgp-gen/ec-keypair-generator "secp160r2"))

(def kp (pgp-gen/generate-keypair ec-gen :ecdsa))
(def private-key (.getPrivateKey kp))
(def public-key (.getPublicKey kp))

(def sig (pgp-sig/sign "pizza" private-key))

(println (pgp-sig/verify "pizza" sig public-key))

(def encoded-sig (pgp/encode sig))
(println (encode64 encoded-sig))

(def private-encoded (pgp/encode private-key))
(def public-encoded (pgp/encode public-key))
(def public-decoded (pgp/decode-public-key public-encoded))
(assert (= (encode64 public-encoded)
           (encode64 (pgp/encode public-decoded))))

(with-open [decoder-stream
            (org.bouncycastle.openpgp.PGPUtil/getDecoderStream
             (bytes/to-input-stream private-encoded))]

  (-> (org.bouncycastle.openpgp.PGPObjectFactory.
       decoder-stream
       (org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator.))
      .nextObject
      println)
  )


(java.security.Security/addProvider (org.bouncycastle.jce.provider.BouncyCastleProvider.))

(def ec-spec (ECNamedCurveTable/getParameterSpec "secp160r2"))

(def kg (java.security.KeyPairGenerator/getInstance "ECDSA" "BC"))
(.initialize kg ec-spec (java.security.SecureRandom.))
(def ec-keypair (.generateKeyPair kg))
(def private (.getPrivate ec-keypair))
(def public (.getPublic ec-keypair))

;; Sign/Verify with Original Keys
(def sig-inst (java.security.Signature/getInstance "SHA256withECDSA"))
(.initSign sig-inst private)
(.update sig-inst (.getBytes "pizza"))
(def signature (.sign sig-inst))

(def verify-inst (java.security.Signature/getInstance "SHA256withECDSA"))
(.initVerify verify-inst public)
(.update verify-inst (.getBytes "pizza"))
(println "verify:" (.verify verify-inst signature))

;; Serialize Keys
(def private-encoded (encode64 (.getEncoded private)))
(def public-encoded (encode64 (.getEncoded public)))

;; De-Serialize Keys
(def public-spec (java.security.spec.X509EncodedKeySpec. (decode64 public-encoded)))
(def private-spec (java.security.spec.PKCS8EncodedKeySpec. (decode64 private-encoded)))
(def key-factory (java.security.KeyFactory/getInstance "ECDSA" "BC"))
(def public-deserialized (.generatePublic key-factory public-spec))
(def private-deserialized (.generatePrivate key-factory private-spec))

;; Verify with de-serialized public key
(def verify-inst (java.security.Signature/getInstance "SHA256withECDSA"))
(.initVerify verify-inst public-deserialized)
(.update verify-inst (.getBytes "pizza"))
(println "verify:" (.verify verify-inst signature))

;; Sign with De-serialized Private
(def sig-inst (java.security.Signature/getInstance "SHA256withECDSA"))
(.initSign sig-inst private-deserialized)
(.update sig-inst (.getBytes "pizza"))
(def signature (.sign sig-inst))

;; Verify with Original Public
(def verify-inst (java.security.Signature/getInstance "SHA256withECDSA"))
(.initVerify verify-inst public)
(.update verify-inst (.getBytes "pizza"))
(println "verify:" (.verify verify-inst signature))
