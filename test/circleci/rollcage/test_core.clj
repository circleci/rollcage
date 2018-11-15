(ns circleci.rollcage.test-core
  (:use clojure.test)
  (:require [bond.james :as bond]
            [schema.test :refer (validate-schemas)]
            [clojure.test :refer :all]
            [clojure.string :as string]
            [circleci.rollcage.core :as client]
            [clj-http.client :as http-client]
            [clojure.test.check.clojure-test :as ct :refer (defspec)]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [speculative.instrument])
  (:import [java.util UUID]
           [clojure.lang ExceptionInfo]))

(use-fixtures :once validate-schemas)

(defn- ends-with?
  "true if a ends with b"
  [a b]
  (let [tail (subvec (vec a) (- (count a) (count b)))]
    (= tail (vec b))))

(deftest ends-with-works?
  (is (ends-with? "foobar" "bar"))
  (is (not (ends-with? "foobaz" "bar"))))

(defspec can-drop-common-heads 1000
  (prop/for-all
    [a (gen/list gen/int)
     b (gen/list gen/int)]
    (let [r (#'client/drop-common-head a b)]
      (or (empty? a)
          (empty? b)
          (ends-with? b r)))))

(deftest dropping-common-frames
  (let [exception [{:exception {:class "OuterClass" :message "Outer"}
                    :frames [{:method "java.lang.Thread.run", :filename "Thread.java", :lineno 745}
                             {:method "java.util.concurrent.ThreadPoolExecutor$Worker.run", :filename "ThreadPoolExecutor.java", :lineno 617}
                             {:method "java.util.concurrent.ThreadPoolExecutor.runWorker", :filename "ThreadPoolExecutor.java", :lineno 1142}
                             {:method "java.util.concurrent.FutureTask.run", :filename "FutureTask.java", :lineno 266}
                             {:method "clojure.lang.AFn.call", :filename "AFn.java", :lineno 18}
                             {:method "clojure.core/binding-conveyor-fn[fn]", :lineno 1142}
                             {:method "java.util.concurrent.FutureTask.run", :filename "FutureTask.java", :lineno 266}
                             {:method "clojure.lang.AFn.call", :filename "AFn.java", :lineno 18}
                             {:method "clojure.core/binding-conveyor-fn[fn]", :filename "core.clj", :lineno 1910}
                             {:method "circle.rollbar.test-client/fn[fn]", :filename "form-init819441637603521333.clj", :lineno 1}]}
                   {:exception {:class "InnerClass" :message "Inner"}
                    :frames [{:method "java.lang.Thread.run", :filename "Thread.java", :lineno 745}
                             {:method "java.util.concurrent.ThreadPoolExecutor$Worker.run", :filename "ThreadPoolExecutor.java", :lineno 617}
                             {:method "java.util.concurrent.ThreadPoolExecutor.runWorker", :filename "ThreadPoolExecutor.java", :lineno 1142}
                             {:method "java.util.concurrent.FutureTask.run", :filename "FutureTask.java", :lineno 266}
                             {:method "clojure.lang.AFn.call", :filename "AFn.java", :lineno 18}
                             {:method "clojure.core/binding-conveyor-fn[fn]", :filename "core.clj", :lineno 1910}
                             {:method "circle.rollbar.test-client/fn[fn]", :filename "form-init819441637603521333.clj", :lineno 1}
                             {:method "clojure.core//", :filename "core.clj", :lineno 985}
                             {:method "clojure.lang.Numbers.divide", :filename "Numbers.java", :lineno 3707}
                             {:method "clojure.lang.Numbers.divide", :filename "Numbers.java", :lineno 156}]}]
        result (#'client/drop-common-substacks exception)]
    (is (= "core.clj" (-> result second :frames first :filename)))
    (is (= 5 (-> result second :frames count)))))

(deftest it-can-parse-exceptions
  (testing "Simple exceptions"
    (let [e (Exception. "test")
          item (first (#'client/build-trace e)) ]
      (is (= "test" (-> item :exception :message)))
      (is (= "class java.lang.Exception" (-> item :exception :class)))
      (let [^String method (-> item :frames last :method)] (is (.startsWith method "circleci.rollcage")))
      ;; Depending on whether the test is run from repl or command line
      ;; the root filename changes.
      (is (#{"main.java" "Thread.java"} (-> item :frames first :filename)))))

  (testing "a nasty triple nested exception"
    (let [e (is (thrown?
                  java.util.concurrent.ExecutionException
                  @(future
                     (try
                       (/ 0)
                       (catch Exception e
                         (throw (Exception. "middle" e)))))))
          item (#'client/build-trace e)
          cause (last item)]

      (testing "the cause"
        (is (= "Divide by zero"
               (-> cause :exception :message)))
        (is (= "class java.lang.ArithmeticException"
               (-> cause :exception :class)))
        (is (= "Numbers.java"
               (-> cause :frames last :filename))))

      (testing "the chain"
        (is (= 3 (count item)))
        (is (= "middle"
               (-> item second :exception :message)))
        (is (= "class java.util.concurrent.ExecutionException"
               (-> item first :exception :class)))))))

(deftest it-can-make-clients
  (let [c (client/client "access-token")]
    (is (= "access-token" (:access-token c))))
  (let [c (client/client "access-token" {:os "DOS"
                                         :environment "alpha"})]
    (is (= "access-token" (:access-token c)))
    (is (= "alpha" (-> c :data :environment)))
    (is (= "DOS" (-> c :data :platform))))

  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Output of client\* does not match schema"
                        (client/client "e" {:hostname 1}))))

(deftest environments-can-be-kw-or-string
  (letfn [(env [e] (-> (client/client "token" {:environment e}) :data :environment))]
    (is (= "test" (env :test)))
    (is (= "dev" (env 'dev)))
    (is (= "staging" (env "staging")))))

(deftest it-can-make-items
  (let [c (client/client "access-token" {})]
    (is (= {:access-token "access-token"
            }
           (select-keys (#'client/make-rollbar c "error" (Exception.) nil nil)
                        [:access-token])))))

(deftest ^:integration test-environment-is-setup
  (is (not (string/blank? (System/getenv "ROLLBAR_ACCESS_TOKEN")))
      "You must specify a ROLLBAR_ACCESS_TOKEN with POST credentials"))

(deftest ^:integration it-calls-result-fn
  (let [token (System/getenv "ROLLBAR_ACCESS_TOKEN")
        e (Exception. "horse")
        p (promise)
        result-fn (fn [ex result]
                    (deliver p [ex result]))]
    (testing "it can send items"
      (let [r (client/client token {:result-fn result-fn})
            {err :err {uuid :uuid} :result} (client/warning r e)]
        (is (zero? err))
        (is (UUID/fromString uuid))
        (is (realized? p))
        (is (= [e {:err 0
                   :result {:id nil, :uuid uuid}}]
               @p))))))

(deftest ^:integration it-can-send-items
  (let [token (System/getenv "ROLLBAR_ACCESS_TOKEN")
        e (Exception. "horse")]
    (testing "it can send items"
      (let [r (client/client token {:code-version "9d95d17105b4e752c46ccf656aaefad5ace50699"})
            {err :err {uuid :uuid} :result} (client/warning r e)]
        (is (zero? err))
        (is (UUID/fromString uuid))))
    (testing "it can handle errors while sending items"
      (let [delivery-exceptions (atom [])
            http-error (ex-info "Some error" {:status 500})
            r (client/client token
                             {:code-version "9d95d17105b4e752c46ccf656aaefad5ace50699"
                              :result-fn (fn [_ {:keys [exception]}]
                                            (swap! delivery-exceptions conj exception))})]
        (with-redefs [http-client/post (fn [& args]
                                         (throw http-error))]
          (is (= {:err 1
                  :exception http-error
                  :message "Some error"}
                 (client/warning r e)))
          (is (= [http-error]
                 @delivery-exceptions)))))))

(deftest ^:integration it-can-send-ex-data
  (let [token (System/getenv "ROLLBAR_ACCESS_TOKEN")
        cause (Exception. "connection error")
        e (ex-info "system error" {:key1 "one" :key2 "two"} cause)]
    (testing "it can send items"
      (let [r (client/client token {:code-version "9d95d17105b4e752c46ccf656aaefad5ace50699"})
            {err :err {uuid :uuid} :result} (client/warning r e)]
        (is (zero? err))
        (is (UUID/fromString uuid))))))

(deftest report-uncaught-exception-test
  (let [p (promise)
        c (assoc (client/client "access-token" {})
                 :send-fn (fn [_ _ item]
                            (deliver p item)
                            {:err 0}))
        e (Exception. "uncaught")
        thread (Thread. "thread")
        {:keys [err]} (#'client/report-uncaught-exception "critical" c e thread)
        result (deref p 0 :failed)]
    (is (zero? err))
    (is (not (= result :failed)))
    (is (= "critical" (get-in result [:data :level])) )
    (is (= "thread" (get-in result [:data :custom :thread-name])))))

(deftest it-handles-no-access-token
  (let [token nil
        cause (Exception. "connection error")
        e (ex-info "system error" {:key1 "one" :key2 "two"} cause)]
    (testing "it can send items"
      (let [r (client/client token {:file-root "/usr/local/src"
                                    :code-version "9d95d17105b4e752c46ccf656aaefad5ace50699"})
            {err :err skipped :skipped {uuid :uuid} :result} (client/warning r e)]
        (is (zero? err))
        (is (true? skipped))
        (is (UUID/fromString uuid))))))

(deftest it-reports-ex-data
  (let [p (promise)
        client (assoc (client/client "access-token" {})
                 :send-fn (fn [_ _ item]
                            (deliver p item)
                            {:err 0}))
        _ (client/critical client (ex-info "outer" {:foo 1} (ex-info "inner" {:bar 2})))
        result (deref p 0 :failed)]
    (is (not (= result :failed)))
    (is (= {:foo 1 :bar 2} (get-in result [:data :custom])))))

(comment
  (run-tests))
