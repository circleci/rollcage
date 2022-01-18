(ns circleci.rollcage.test-throwables
  (:require [circleci.rollcage.throwables :as throwables]
            [clojure.test :refer (deftest is are)]
            [speculative.instrument]))

(deftest cause-seq-works
  (let [a (ex-info "a" {:name "a"})
        b (ex-info "b" {:name "b"
                        :b-added "this"} a)
        c (ex-info "c" {:name "c"
                        :c-added "this"} b)]
    (is (= [a] (#'throwables/cause-seq a)))
    (is (= [c b a] (#'throwables/cause-seq c)))
    (is (= {:name "a"} (throwables/merge-ex-data a)))
    (is (= {:name "a"
            :b-added "this"
            :c-added "this"}
           (throwables/merge-ex-data c)))))

(deftest select-exception-key-works
  (are [input expected] (= expected (throwables/select-exception-key input))
    nil         throwables/default-exception-key
    ""          throwables/default-exception-key
    123         "123"
    "some-key"  "some-key"
    :some-key   ":some-key"
    identity    (str identity)))

(deftest merged-ex-data-works
  (let [simple-error-data {:a 1 :b 2}
        simple-error      (ex-info "An error" simple-error-data)
        nested-error      (ex-info "outer" {:foo 2 :bar 3} (ex-info "inner" {:foo 1}))]
    (are [args expected]  (= expected
                             (apply throwables/merged-ex-data args))
      [{} simple-error]
      simple-error-data

      [{} nested-error]
      {:foo 1 :bar 3}

      [{:indexed-ex-data false} nested-error]
      {:foo 1 :bar 3}

      [{:indexed-ex-data true} simple-error]
      simple-error-data

      [{:indexed-ex-data true} nested-error]
      {0 {"__exception" "inner"
          :foo          1}
       1 {"__exception" "outer"
          :foo          2
          :bar          3}}

      [{:indexed-ex-data "KEY"} nested-error]
      {0 {"KEY" "inner"
          :foo  1}
       1 {"KEY" "outer"
          :foo  2
          :bar  3}})))
