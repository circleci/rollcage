(ns circleci.rollcage.test-ring-middleware
  (:require [bond.james :as bond]
            [circleci.rollcage.ring-middleware :as middleware]
            [circleci.rollcage.shell :as rollcage]
            [clojure.test :refer (deftest is testing)]))

(deftest wrap-rollbar-works
  (let [error (ex-info "something bad happened" {})
        dummy-ring-handler (fn [_]
                             (throw error)
                             {:status 200})
        dummy-request {:uri "/" :params {}}]
    (testing "Reports rollbars when rollcage-client is truthy"
      (let [wrapped-handler (middleware/wrap-rollbar dummy-ring-handler)]
        (bond/with-stub [rollcage/error]
          (let [result (try
                         (wrapped-handler dummy-request)
                         (catch Exception e
                           e))]
            (is (= result error))
            (is (= [[error {:url "/"}]]
                   (->> rollcage/error
                        bond/calls
                        (map :args))))))))))

(clojure.test/run-tests)
