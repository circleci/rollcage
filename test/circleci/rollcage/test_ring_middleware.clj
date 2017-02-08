(ns circleci.rollcage.test-ring-middleware
  (:require [bond.james :as bond]
            [circleci.rollcage.ring-middleware :as middleware]
            [circleci.rollcage.core :as rollcage]
            [clojure.test :refer (deftest)]))

(deftest wrap-rollbar-works
  (let [dummy-ring-handler (fn [_]
                             (throw (ex-info "something bad happened" {})))
        dummy-rollcage-client {}
        dummy-request {:uri "/" :params {}}
        wrapped-handler (middleware/wrap-rollbar dummy-ring-handler
                                                 dummy-rollcage-client)]
    (bond/with-stub [rollcage/error]
      (try
        (wrapped-handler dummy-request)
        (catch Exception e
          (is (= [[dummy-rollcage-client e {:uri "/"}]]
                 (->> rollcage/error
                      bond/calls
                      (map :args)))))))))
