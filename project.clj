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
  :dependencies [[org.clojure/clojure "1.11.2"]
                 [cheshire "5.13.0"]
                 [clj-http "3.13.0"]
                 [prismatic/schema "1.4.1"]
                 [clj-stacktrace "0.2.8"]
                 [org.clojure/core.memoize "1.0.236"]
                 [org.clojure/tools.logging "0.5.0"]]
  :plugins [[lein-shell "0.5.0"]
            [lein-pprint "1.3.2"]
            [lein-test-out "0.3.1" :exclusions [org.clojure/clojure]]]
  :global-vars {*warn-on-reflection* true}
  :profiles {:dev {:dependencies [[org.clojure/test.check "1.1.1"]
                                  [circleci/bond "0.6.0"]
                                  [speculative "0.0.3"]]}}
  :test-selectors {:all         (constantly true)
                   :default     (constantly true)
                   :unit        (complement :integration)
                   :integration :integration})
