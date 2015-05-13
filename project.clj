(defproject circleci/rollcage "0.1.0"
  :description "A Clojure client for Rollbar"
  :url "http://github.com/circleci/rollcage"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :global-vars  {*warn-on-reflection* true}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [cheshire "5.4.0"]
                 [clj-http "1.1.0"]
                 [clj-stacktrace "0.2.8"]]
  :plugins [[lein-test-out "0.3.1" :exclusions [org.clojure/clojure]]]
  :profiles {:dev {:dependencies [[bond "0.2.5"]]}})
