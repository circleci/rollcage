(ns circleci.rollcage.test-core
  (:use clojure.test)
  (:require [bond.james :as bond]
            [schema.test :refer (validate-schemas)]
            [clojure.test :refer :all]
            [clojure.string :as string]
            [clojure.set :refer [subset?]]
            [circleci.rollcage.core :as client]
            [circleci.rollcage.http :as http]
            [clojure.test.check.clojure-test :as ct :refer (defspec)]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop])
  (:import [java.util UUID]
           [clojure.lang ExceptionInfo]))

(use-fixtures :once validate-schemas)

(defn- ends-with?
  "true if a ends with b"
  [a b]
  (let [tail (subvec (vec a) (- (count a) (count b)))]
    (= tail (vec b))))

(defrecord TestHttpClient []
  http/HttpClient
  (post [this ex params]
    "{\"test\":\"ok\"}"))

(deftest ends-with-works?
  (is (ends-with? "foobar" "bar"))
  (is (not (ends-with? "foobaz" "bar"))))

(defspec can-drop-common-heads 1000
  (prop/for-all
    [a (gen/list gen/int)
     b (gen/list gen/int)]
    (let [r (client/drop-common-head a b)]
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
        result (client/drop-common-substacks exception)]
    (is (= "core.clj" (-> result second :frames first :filename)))
    (is (= 5 (-> result second :frames count)))))

(deftest it-can-parse-exceptions
  (testing "Simple exceptions"
    (let [e (Exception. "test")
          item (first (client/build-trace e)) ]
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
          item (client/build-trace e)
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

(deftest it-can-use-custom-http-clients
  (let [http-client (->TestHttpClient)
        c (client/client "access-token" {:http-client http-client})]
    (is (= http-client (:http-client c)))))

(deftest environments-can-be-kw-or-string
  (letfn [(env [e] (-> (client/client "token" {:environment e}) :data :environment))]
    (is (= "test" (env :test)))
    (is (= "dev" (env 'dev)))
    (is (= "staging" (env "staging")))))

(deftest it-can-make-items
  (let [c (client/client "access-token" {})
        make-item (partial client/make-rollbar c "error" (Exception.))]
    (let [item (make-item nil nil)]
      (is (apply = (map #(select-keys % [:access-token :http-client])
                        [c item]))))

    (let [item (make-item "http://example.com" nil)]
      (is (= "http://example.com" (get-in item [:data :request :url]))))

    (let [req {:url "http://example.com"
               :params {:param-1 1 :param-2 2}
               :headers {"Content-Type" "text/plain"}}
          item (make-item nil {:request req})]
      (is (= req (get-in item [:data :request]))))

    (let [item (make-item nil {:context "project#context"})]
      (is (= "project#context" (get-in item [:data :context]))))

    (let [person {:email "email@example.com" :id "123" :username "some-user"}
          item (make-item nil {:person person})]
      (is (= person (get-in item [:data :person]))))

    (let [custom {:some-key "custom"}
          item (make-item nil custom)]
      (is (= custom (get-in item [:data :custom]))))

    (let [item (make-item "http://url1.com" {:request {:url "http://url2.com"}})]
      (is (= "http://url1.com" (get-in item [:data :request :url]))))))

(deftest it-can-send-items-via-custom-http-client
  (let [http-client (->TestHttpClient)
        c (client/client "access-token" {:http-client http-client})
        r (client/notify "error" c (Exception.))]
    (is (= {:test "ok"} r))))

(deftest ^:integration test-environment-is-setup
  (is (not (string/blank? (System/getenv "ROLLBAR_ACCESS_TOKEN")))
      "You must specify a ROLLBAR_ACCESS_TOKEN with POST credentials"))

(deftest ^:integration it-can-send-items
  (let [token (System/getenv "ROLLBAR_ACCESS_TOKEN")
        r (client/client token {:code-version "9d95d17105b4e752c46ccf656aaefad5ace50699"})
        e (Exception. "horse")
        {err :err {uuid :uuid } :result} (client/warning r e) ]
    (is (zero? err))
    (is (UUID/fromString uuid))))

(deftest report-uncaught-exception-test
  (with-redefs [client/send-item (fn [e r]
                                   (if (and
                                         (= "error" (get-in r [:data :level]))
                                         (= "thread" (get-in r [:data :custom :thread])))
                                     {:err 0}
                                     {:err 1}))]
    (let [c (client/client "access-token" {})
          e (Exception. "uncaught")
          thread (Thread. "thread")
          {:keys [err]} (client/report-uncaught-exception "error" c e thread)]
      (is (zero? err)))))