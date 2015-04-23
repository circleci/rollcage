(ns circleci.rollcage.test-core
  (:require [bond.james :as bond]
            [clojure.test :refer :all]
            [circleci.rollcage.core :as client]))

(deftest dropping-common-frames
  (are [expected x y] (= expected (client/drop-common-head x y))
       [] [] []
       [4 5 6] [1 2 3] [1 2 3 4 5 6]
       [] [1 2 3 4] []
       [4 5 6] [] [4 5 6]
       [6] [1] [6]
       [] [1 2 3] [1 2 3]
       [] [1 2 3 4 5] [1 2])
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
