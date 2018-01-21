(ns circleci.rollcage.test-shell
  (:require [bond.james :as bond]
            [circleci.rollcage.shell :as shell]
            [clojure.test :refer :all])
  (:import [java.util UUID]
           [clojure.lang ExceptionInfo]))

(deftest it-can-deal-with-default-values
  (is (= ::foo (shell/or-default nil (constantly ::foo))))
  (is (= ::foo (shell/or-default "" (constantly ::foo))))
  (is (= "bar" (shell/or-default "bar" (constantly ::foo))))
  (is (= "bar" (shell/or-default :bar (constantly ::foo)))))

(deftest it-can-create-clients
  (let [client (shell/create-client {})]
    (is (= {:access-token nil
            :data {:environment "production"
                   :platform "Mac OS X"
                   :language "Java"
                   :framework "Ring"
                   :notifier {:name "Rollcage"}
                   :server {:host "gears.local"
                            :root "/Users/marc/dev/circleci/rollcage", :code_version nil}}}

           (select-keys client [:access-token :data])))))

(run-tests)
