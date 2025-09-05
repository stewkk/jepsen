(ns jepsen.iu9db
  (:require [jepsen [cli :as cli]
             [tests :as tests]]
            [jepsen.os.debian :as debian]))

(defn etcd-test
  "Given an options map from the command line runner (e.g. :nodes, :ssh,
  :concurrency, ...), constructs a test map."
  [opts]
  (merge tests/noop-test
         opts
         {:name "simple-test"
          :os debian/os
          :pure-generators true
          }))

(def cli-opts
  "Additional command line options."
  [])

(defn -main
  "Handles command line arguments. Can either run a test, or a web server for
  browsing results."
  [& args]
  (cli/run! (merge (cli/single-test-cmd {:test-fn etcd-test
                                         :opt-spec cli-opts})
                   (cli/serve-cmd))
            args))
