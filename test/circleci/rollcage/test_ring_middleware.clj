(ns circleci.rollcage.test-ring-middleware
  (:require [bond.james :as bond]
            [circleci.rollcage.ring-middleware :as middleware]
            [circleci.rollcage :as rollcage]
            [circleci.rollcage.core :as core]
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
            (is (= [[error {:request {:url "/"}}]]
                   (->> rollcage/error
                        bond/calls
                        (map :args))))))))))

(deftest can-add-to-env
  (let [error (ex-info "something bad happened" {})
        dummy-ring-handler (fn [_]
                             (middleware/add-to-custom! {:foo 1
                                                         "bar" true})
                             (middleware/add-to-person! {:baz "woo"
                                                         "qux" 2.3})
                             (throw error))
        dummy-request {:uri "/foo"
                       :remote-addr "1.2.3.4"
                       :request-method :head}
        wrapped-handler (middleware/wrap-rollbar dummy-ring-handler)]

    (let [item (promise)]
      (binding [core/*send* (fn [i]
                              (deliver item i))]
        (let [result (is (thrown? Exception (wrapped-handler dummy-request)))]
          (is (= result error))
          (is (realized? item))
          
          (let [{:keys [data]} (deref item 0 ::failed)]
            (is (= {:url "/foo", :user-ip "1.2.3.4", :method "HEAD"}
                   (:request data)))
            (is (= {:foo 1, "bar" true}
                   (:custom data)))
            (is (= {:baz "woo", "qux" 2.3}
                   (:person data)))))))))

(can-add-to-env)
