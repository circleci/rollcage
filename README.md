# Rollcage [![Circle CI](https://circleci.com/gh/circleci/rollcage.svg?style=svg)](https://circleci.com/gh/circleci/rollcage) [![codecov.io](https://codecov.io/github/circleci/rollcage/coverage.svg?branch=master)](https://codecov.io/github/circleci/rollcage?branch=master)

A Clojure client for [Rollbar](http://rollbar.com)


## Usage

Rollcage is available on [Clojars](https://clojars.org/circleci/rollcage). Add the following to your `project.clj`:

```Clojure
[circleci/rollcage "1.0.148"]
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

You can also setup handler for all UncaughtExceptions.
Call this fn during start-up procedure to ensure all uncaught exceptions
will be sent to Rollbar.

user=> (rollcage/setup-uncaught-exception-handler r)

See the full [API docs](https://circleci.github.io/rollcage/) for more
information.

## Testing

A full CI suite is [run on CircleCI](https://circleci.com/gh/circleci/rollcage).
You can run the unit-test suite locally by running `lein test`. Some tests
require access to Rollbar, with a valid access token that has permission to post
server items. The token should be specified in the ROLLBAR_ACCESS_TOKEN
environment variable.

```bash
$ ROLLBAR_ACCESS_TOKEN=<your token> lein test
```

The tests that require access to Rollbar are annotated with the `:integration`
metadata tag. You can exclude these by using the `:unit` test selector.

```bash
$ lein test :unit
```


## Releasing

Releases are published [to Clojars under the CircleCI organisation](https://clojars.org/circleci/rollcage).
You can publish new SNAPSHOT version of Rollcage using leiningen:

```bash
$ lein deploy clojars
```

You can release a new version of Rollcage by editing the version string in
`project.clj` according to [semver](http://semver.org/) and removing the
`-SNAPSHOT` qualifier. Then run

```bash
$ lein deploy clojars
```

## License

Distributed under the [Eclipse Public License](http://www.eclipse.org/legal/epl-v10.html).
