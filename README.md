# Rollcage [![Circle CI](https://circleci.com/gh/circleci/rollcage.svg?style=svg)](https://circleci.com/gh/circleci/rollcage)

A Clojure client for [Rollbar](http://rollbar.com)


## Usage

Rollcage is available on [Clojars](https://clojars.org/circleci/rollcage). Add the following to your `project.clj`:

```Clojure
[circleci/rollcage "0.1.0-SNAPSHOT"]
```

You can send exceptions like this:

```Clojure
user=> (require '[circleci.rollcage.core :as rollcage])

user=> (def r (rollcage/client "access-token" {:environment "staging"}))

user=> (try
  #_=>   (/ 0)
  #_=>   (catch Exception e
  #_=>     (rollcage/error r e)))
{:err 0, :result {:id nil, :uuid "1cb0c8bf-3942-4553-a5c4-8adc5d55ed8f"}}
```

## License

Distributed under the [Eclipse Public License](http://www.eclipse.org/legal/epl-v10.html).
