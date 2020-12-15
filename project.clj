(defproject circleci/rollcage
  (format "1.0.%s" (or (System/getenv "CIRCLE_BUILD_NUM")
                       "0-SNAPSHOT"))
  :description "A Clojure client for Rollbar"
  :url "http://github.com/circleci/rollcage"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :repositories [["releases" {:url "https://clojars.org/repo"
                              :username :env/clojars_username
                              :password :env/clojars_token
                              :sign-releases false}]
                 ["snapshots" {:url "https://clojars.org/repo"
                               :username :env/clojars_username
                               :password :env/clojars_token
                               :sign-releases false}]]
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [cheshire "5.8.1"]
                 [clj-http "2.0.0"]
                 [prismatic/schema "1.1.9"]
                 [clj-stacktrace "0.2.8"]
                 [org.clojure/core.memoize "1.0.236"]
                 [org.clojure/tools.logging "0.4.0"]]
  :plugins [[lein-cloverage "1.0.6"]
            [lein-shell "0.5.0"]
            [lein-pprint "1.1.1"]
            [lein-test-out "0.3.1" :exclusions [org.clojure/clojure]]]
  :global-vars {*warn-on-reflection* true}
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.10.1"]
                                  [org.clojure/test.check "0.9.0"]
                                  [circleci/bond "0.2.9"]
                                  [speculative "0.0.3"]]}}
  :test-selectors {:all         (constantly true)
                   :default     (constantly true)
                   :unit        (complement :integration)
                   :integration :integration})
