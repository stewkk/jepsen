(ns jepsen.iu9db
  (:require [jepsen.cli :as cli]
            [jepsen.workloads.simple :as simple]))


(def cli-opts
  "Additional command line options."
  [])

(defn -main
  "Handles command line arguments. Can either run a test, or a web server for
  browsing results."
  [& args]
  (cli/run! (merge (cli/single-test-cmd {:test-fn simple/simple-test
                                         :opt-spec cli-opts})
                   (cli/serve-cmd))
            args))
