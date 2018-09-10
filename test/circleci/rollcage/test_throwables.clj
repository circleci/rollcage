(ns circleci.rollcage.test-throwables
  (:require [circleci.rollcage.throwables :as throwables]
            [clojure.test :refer (deftest is testing)]))

(deftest cause-seq-works
  (let [a (ex-info "a" {:name "a"})
        b (ex-info "b" {:name "b"
                        :b-added "this"} a)
        c (ex-info "c" {:name "c"
                        :c-added "this"} b)]
    (is (= [a] (#'throwables/cause-seq a)))
    (is (= [c b a] (#'throwables/cause-seq c)))
    (is (= {:name "a"} (throwables/merged-ex-data a)))
    (is (= {:name "a"
            :b-added "this"
            :c-added "this"}
           (throwables/merged-ex-data c)))))
