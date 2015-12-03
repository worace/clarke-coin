(ns block-chain.wallet-test
  (:require [clojure.test :refer :all]
            [block-chain.wallet :refer :all]))

;; (def message "jFd5MRjGs3S19XIblzdkVkEwUc7A7WRPdWtj6JPXuqU6k/ue1sLRLRhc0inu\nNUglbx3TJTcq6i2FvIA1wb7LnMfw9MiFVs/wbxhtQFmx9VYKHLQq7pqKiYkZ\n6Mqtr1SiUT5e6OFc+6WEIj6GzaF6LRU9osGmUQs40iPQr5016XafRCDeIval\nNo8na5C2WZu4m4ZYadnOwDkAbuk4Vhd7xxR+F433Qitncux8oKHhVOpR9yYV\n7JzNVPzC5o+fX8PNns39Pcb91m3Z243GOl8xlfBFPYa0Wytd9DJB13MYAiWQ\n6gAGUa3kYJJIqMQU3Yyz0DCvRtoJiFXacrJoAvQ5zg==")

;; (let [pubkey (read-pem-public-key-from-string sample-pub-key-string)
;;       encrypted (encrypt (.getBytes "Hi Josh!") pubkey)]
;;   (println "Encrypted 'Hi Josh!':")
;;   (println (encode64 encrypted)))

(deftest test-base-64
  (let [b (.getBytes "pizza")
        chars (map char "pizza")]
    (is (= chars (map char (decode64 (encode64 b)))))
    (is (= "cGl6emE=" (encode64 (.getBytes "pizza"))))
    (is (= chars (map char (decode64 "cGl6emE="))))))

(deftest test-reading-keys
  (let [pub-key-pem (slurp "./test/sample_public_key.pem")
        priv-key-pem (slurp "./test/sample_private_key.pem")]
    (is (= sun.security.rsa.RSAPublicKeyImpl (type (pem-string->pub-key pub-key-pem))))
    (is (= sun.security.rsa.RSAPublicKeyImpl
           (type (pem-file->public-key "./test/sample_public_key.pem"))))
    (is (= pub-key-pem (key->pem-string (pem-string->pub-key pub-key-pem) "PUBLIC KEY")))
    (is (= java.security.KeyPair
           (type (pem-file->private-key "./test/sample_private_key.pem"))))
    (println priv-key-pem)
    (println (private-key->pem-string
              (.getPrivate (pem-file->private-key "./test/sample_private_key.pem"))
              ))
    #_(is (= priv-key-pem
           (key->pem-string (.getPrivate (pem-file->private-key "./test/sample_private_key.pem")))))))

(run-tests)
