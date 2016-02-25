(ns block-chain.message-handlers
  (:require [block-chain.utils :refer :all]
            [block-chain.chain :as bc]
            [block-chain.db :as db]
            [block-chain.wallet :as wallet]
            [block-chain.pem :as pem]
            [block-chain.miner :as miner]
            [block-chain.transactions :as txns]))

(defn echo [msg sock-info]
  msg)

(defn pong [msg sock-info]
  {:message-type "pong" :payload (:payload msg)})

(defn get-peers [msg sock-info]
  {:message-type "peers" :payload @db/peers})

(defn add-peer [msg sock-info]
  (let [host (:remote-address sock-info)
        port (:port (:payload msg))]
    (swap! db/peers conj {:host host :port port})
    {:message-type "peers" :payload @db/peers}))

(defn remove-peer [msg sock-info]
  (let [host (:remote-address sock-info)
        port (:port (:payload msg))]
    (swap! db/peers
           clojure.set/difference
           #{{:host host :port port}})
    {:message-type "peers" :payload @db/peers}))

(defn get-balance [msg sock-info]
  (let [key (:payload msg)
        balance (bc/balance key @db/block-chain)]
    {:message-type "balance"
     :payload {:key key :balance balance}}))

(defn get-transaction-pool [msg sock-info]
  {:message-type "transaction_pool"
   :payload (into [] @db/transaction-pool)})

(defn add-transaction [msg sock-info]
  (swap! db/transaction-pool conj (:payload msg)))

(defn get-block-height [msg sock-info]
  {:message-type "block_height"
   :payload (count @db/block-chain)})

(defn get-latest-block [msg sock-info]
  {:message-type "latest_block"
   :payload (last @db/block-chain)})

(defn get-blocks [msg sock-info]
  {:message-type "blocks"
   :payload @db/block-chain})

(defn get-block [msg sock-info]
  {:message-type "block_info"
   :payload (bc/block-by-hash (:payload msg)
                              @db/block-chain)})

(defn get-transaction [msg sock-info]
  {:message-type "transaction_info"
   :payload (bc/txn-by-hash (:payload msg)
                              @db/block-chain)})


(defn make-payment [msg sock-info]
  ;; what if it fails?
  ;; this should forward the new transaction to other nodes we're connected to
  ;; {:message-type make_payment, :payload {:private-pem pizza, :address pizza2, :amount 15}}
  ;; get private pem from the message and turn it into a key
  ;; generate new payment transaction for that amount
  ;; use that key to sign that txn
  ;; ...


  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  ;;;;;;;;; EXAMPLE ;;;;;;;;;;;;;;;;;;;;;;
  (let [sample-private-pem "-----BEGIN RSA PRIVATE KEY-----\nMIIEowIBAAKCAQEAuFl76216Veu5/H2MM4lONFOuZLGcwxeUQzdmW2g+da5mmjyV\n3RiuYueDJFlAgx2iDASQM+rK1qKp7lj352DU3gABqJ5Tk1mRvGHTGz+aP4sj8CKU\nnjJIQVmmleiRZ47wRDsnrg9N0XyfW+aiPKxljvr1pkKJmryO+u2d69Tc69bNsqpG\nzFLTdO3w1k/jxa0pUAQNqf11MJSrzF7u/Z+8maqFZlzZ5o1LgqTLMpeFg0pcMIKu\nZb9yQ1IKqOjLsvTvYYyBbNU31FD8qVY/R64zbrIYbfWXNiUrYOXyIq7rqegLf3fx\n+aJGgwUOGYr2MJjY+ZR5Z+cIKJiAgNnpkBWRhwIDAQABAoIBAAQ11Av/iduPXbS/\nkwe2sJOVsuDElhYPxH/rTCf9z8voUok8HO0I716oTHgWnml3p7Bs6MnOwhAvuMz0\ndOMbbCRbkZ0P9KHSptUg9NOyHwFct6f4QJ0lQOiaxv5fao9CnqGw1NcRJJyKcA/T\nsVRmJUs9tXSbhhZH6woM8MSeLbRKnVEFNY4QLDqvCVZ1FJvnZTfEtHn3KTwD/wLI\nylHl4LhH3xKqMU4zbQye+Rb9EcKIhU9NEuq8ls85OQ1EuB01Z2vJor1ZXV6HSopk\nCe0aMk/lyL0RRjNh88wkoSKjocL4GYlh6XB0kAkGqqRqu2qcvhGwL/ToE3KSqAaq\nh6S98ckCgYEA3zDZa8ad49XqXA9ye4KoHHLhIMeXBsH3aIrXLbuxCSrchcPlUlCc\nWGQVlo6pJZi/JvVhygS7n2tpiyxjzOeGmi/+iaNmnbyAp4yUFVI1WOzhhHPMXl1C\n2SKBO+7ZzDDRAuDzfiWh4w7gKyY1HnLlA8t0fwWJCxEjNNJrWWRh8P8CgYEA03L0\nRRGlYPlE/iiQFoPo58/2ehuMSvx0Ut/1yulh0l5qUNGk9Lxic5lKYRCXV/ZsMpOB\nV3A2lyTqB7Y7VsyKJM2r7zErgGqEIsSwr9gRNZiHZbEgcpuGTU00x6i9o8Bmtlwl\nurdr0rbQN9Iy4hNLq3E6wp3KZ4Y23LoWQYccV3kCgYBAMsnnYVsbJPXjgyH3+u4m\nOLke96oubG6g6Ibahcl3jJef9mgpFDzUg/1dsC1hPh2FtEFrnY7mXTwfpyYR02r5\nQOLjblfe+VC8YQhbVak4qfJ4aeKYJhVLZcxsk9icDlKglFoVp/d321iJsNTIrF3J\niKU6wDnaMBTDZd8w+FnfuwKBgQCmu/i+FgIQi77Jo3arKm0XjjdPZjWkU5nuV8fb\nr1iBqA2xmP64NWx0tyWdkJWmuRvsbcAKEOQbHvKkDiCQe01bZAr13BHvew6i0/J3\nVquLVbE+kQODG/w1LHI4FmkjnO3hUpw1tbkCOUHLIqcY+horIGgzWnVmWMEeIGa4\nWTASEQKBgCZfiahHX11/aOfhIDLxt/RJWuUjk2xm7Vke4SLJ9xFQCkXz3bU784CE\nybIyDq3RY0Gx07JzzBPjrSsMDFcwjdmrfed+/6r2Pam3Npk56pgwGktbtz2xHZBY\nWQpg/xjNoipk924dWe1mdtMwbS/5qXFrZ7VhcbdQciS+QK+fkY+f\n-----END RSA PRIVATE KEY-----\n"
        sample-kp (wallet/key-map (pem/pem-string->key-pair sample-private-pem))
        recipient-address "-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAl2OTWmYziww0VeGqq6TA\nJMqVyr5hFg/k0hNrro0FsQFv5UBGRpDAxud40Wo/TU5Tk0hkC8nkdXq/Gyt+NDFi\nbZfKfGZuQljxqbNM+VEHCrYx+9ybFhfD9WiO6SYRn3sYqXgfXbnl+fKH0dTDeCOh\nFGfOpk/OjcfTqMlE1cWfNr5ex3QvYOZoJRywcD/+S2fV1u7aRhhxLI3jhzzocw7I\nEnsGVHeiSlxflpkJ7SxHVvSrCKK9K2hf5qkg6PGfD1GV4czCdYLWcjUOaJ7jYWbN\nocI2GM5/arMxfTNhNkTz1u7dyQIIrZ75v+lj32MkVRXOw6OaNZ1/Ni2jIfCHbau1\nWwIDAQAB\n-----END PUBLIC KEY-----\n"
        amount 15
        txn (miner/generate-payment sample-kp recipient-address amount @db/block-chain 0)]
    (swap! db/transaction-pool conj txn))

  {:message-type "transaction_created"
   :payload "lol"})

(def message-handlers
  {"echo" echo
   "ping" pong
   "get_peers" get-peers
   "add_peer" add-peer
   "remove_peer" remove-peer
   "get_balance" get-balance
   "get_block_height" get-block-height
   "get_latest_block" get-latest-block
   "get_transaction_pool" get-transaction-pool
   "get_blocks" get-blocks
   "get_block" get-block
   "get_transaction" get-transaction
   "add_transaction" add-transaction
   "make_payment" make-payment})


(defn handler [msg sock-info]
  (let [handler-fn (get message-handlers
                        (:message-type msg)
                        echo)]
    (handler-fn msg sock-info)))
