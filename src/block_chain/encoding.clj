(ns block-chain.encoding)

(defn decode64 [str] (.decode (java.util.Base64/getDecoder) str))

(defn encode64 [bytes] (.encodeToString (java.util.Base64/getEncoder) bytes))
