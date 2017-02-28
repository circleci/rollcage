(ns circleci.rollcage.test-ring-middleware
  (:require [bond.james :as bond]
            [circleci.rollcage.ring-middleware :as middleware]
            [circleci.rollcage.core :as rollcage]
            [clojure.test :refer (deftest is testing)]))

(deftest wrap-rollbar-works
  (let [error (ex-info "something bad happened" {})
        dummy-ring-handler (fn [_]
                             (throw error)
                             {:status 200})
        dummy-request {:uri "/" :params {}}]
    (testing "Reports rollbars when rollcage-client is truthy"
      (let [dummy-rollcage-client {}
            wrapped-handler (middleware/wrap-rollbar dummy-ring-handler
                                                     dummy-rollcage-client)]
        (bond/with-stub [rollcage/error]
          (let [result (try
                         (wrapped-handler dummy-request)
                         (catch Exception e
                           e))]
            (is (= result error))
            (is (= [[dummy-rollcage-client error {:uri "/"}]]
                   (->> rollcage/error
                        bond/calls
                        (map :args))))))))
    (testing "Doesn't report rollbars when rollcage-client is `nil`"
      (let [dummy-rollcage-client nil
            wrapped-handler (middleware/wrap-rollbar dummy-ring-handler
                                                     dummy-rollcage-client)]
        (bond/with-stub [rollcage/error]
          (let [result (try
                         (wrapped-handler dummy-request)
                         (catch Exception e
                           e))]
            (is (= result error))
            (is (= 0 (-> rollcage/error bond/calls count)))))))))
