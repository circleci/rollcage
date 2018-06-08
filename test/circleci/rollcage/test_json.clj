(ns circleci.rollcage.test-json
  (:require [clojure.test :refer (are deftest is)]
            [bond.james :as bond]
            [circleci.rollcage.json :as json]))

(deftest encode-for-normal-things-works-without-calling-the-backstop
  (bond/with-spy [json/backstop-encoder]
    (are [input expected]
         (= expected (json/encode input))

         {} "{}"
         {:foo :bar} "{\"foo\":\"bar\"}"
         {:foo-bar :baz-quux} "{\"foo_bar\":\"baz-quux\"}")
    (is (= 0 (count (bond/calls json/backstop-encoder))))))

(deftest encoding-odd-objects-does-not-throw
  (are [input expected]
       (= expected (json/encode input))

       {:class (type 42)}
       "{\"class\":\"class java.lang.Long\"}"

       {:ip (java.net.InetAddress/getLoopbackAddress)}
       "{\"ip\":\"localhost/127.0.0.1\"}"

       {:class (type 42)
        :integer 42
        :string "string"}
       "{\"class\":\"class java.lang.Long\",\"integer\":42,\"string\":\"string\"}"))

(deftest decoding-works-as-expected
  (are [input expected]
       (= expected (json/decode input))
       
       "" nil
       "{}" {}
       "{\"foo\":\"bar\"}" {:foo "bar"}))
