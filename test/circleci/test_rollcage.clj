(ns circleci.test-rollcage
  (:require [bond.james :as bond]
            [circleci.rollcage :as rollcage]
            [circleci.rollcage.core :as core]
            [clojure.test :refer :all])
  (:import [java.util UUID]
           [clojure.lang ExceptionInfo]))

(deftest it-can-deal-with-default-values
  (is (= ::foo (#'rollcage/or-default nil (constantly ::foo))))
  (is (= ::foo (#'rollcage/or-default "" (constantly ::foo))))
  (is (= "bar" (#'rollcage/or-default "bar" (constantly ::foo))))
  (is (= "bar" (#'rollcage/or-default :bar (constantly ::foo)))))

(deftest it-can-create-clients
  (let [client (rollcage/create-client {})]
    (is (= {:access-token nil
            :data {:environment "production"
                   :platform "Mac OS X"
                   :language "Java"
                   :framework "Ring"
                   :notifier {:name "Rollcage"}
                   :server {:host "gears.local"
                            :root "/Users/marc/dev/circleci/rollcage", :code_version nil}}}

           (select-keys client [:access-token :data])))))

(deftest it-can-report-uncaught-exceptions
  (let [item (promise)
        ex (ex-info "test" {})]
    ;(binding [core/*send* (fn [i]
                            ;(deliver item i))]
      (is (nil? (rollcage/report-uncaught-exception ex (Thread/currentThread)))
    ;(is (realized? item))
    ;(is (= {} @item)))
  )))
    ;)

;(println "hi")
(it-can-report-uncaught-exceptions)
