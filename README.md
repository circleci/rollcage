# Rollcage [![Circle CI](https://circleci.com/gh/circleci/rollcage.svg?style=svg)](https://circleci.com/gh/circleci/rollcage)

A Clojure client for [Rollbar](http://rollbar.com)


## Usage

Rollcage is available on Clojars. Add the following to your `project.clj`:

```Clojure
[circleci/rollcage "0.1.0-SNAPSHOT"]
```

You can send exceptions like this:

```Clojure
user=> (require '[circleci.rollcage.core :as rollcage])
nil

user=> (rollcage/notify "Darwin"
  #_=>                  "astrotrain"
  #_=>                  "c3dc8492f1e423dcad3a6349e1497f839363b5be"
  #_=>                  "post-item-acces-token"
  #_=>                  "development"
  #_=>                  "/Users/marc/dev/rollcage"
  #_=>                  (Exception. "Test")
  #_=>                  {:url "http://localhost:8080" :params {}})
{:err 0, :result {:id nil, :uuid "1cb0c8bf-3942-4553-a5c4-8adc5d55ed8f"}}
```

## License

Distributed under the [Eclipse Public License](http://www.eclipse.org/legal/epl-v10.html).
