(defproject circleci/rollcage "0.2.2-SNAPSHOT"
  :description "A Clojure client for Rollbar"
  :url "http://github.com/circleci/rollcage"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :deploy-repositories ^:replace [["clojars" {:url "https://clojars.org/repo"
                                              :username [:gpg :env/clojars_username]
                                              :password [:gpg :env/clojars_password]
                                              :sign-releases false}]]
  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ;["shell" "git" "commit" "-am" "[skip ci] Version ${:version}"]
                  ["vcs" "tag"]
                  ["deploy" "clojars"]]
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [cheshire "5.4.0"]
                 [clj-http "2.0.0"]
                 [prismatic/schema "0.4.3"]
                 [clj-stacktrace "0.2.8"]]
  :plugins [[lein-cloverage "1.0.6"]
            [lein-shell "0.5.0"]
            [lein-test-out "0.3.1" :exclusions [org.clojure/clojure]]]
  :profiles {:dev {:dependencies [[org.clojure/test.check "0.7.0"]
                                  [bond "0.2.5"]]}}
  :test-selectors {:all         (constantly true)
                   :default     (constantly true)
                   :unit        (complement :integration)
                   :integration :integration})
