(ns jepsen.iu9db
  (:require [clojure.tools.logging :as log]
            [jepsen [cli :as cli]
             [tests :as tests]
             [db :as db]
             [client :as client]
             [generator :as gen]]
            [jepsen.os.ubuntu :as ubuntu]
            [jepsen.control.util :as cu]
            [jepsen.control :as c]
            ))

(def dir "/opt")
(def binary "iu9-db")
(def pidfile (str dir "/iu9db.pid"))
(def logfile (str dir "/iu9db.log"))
(def datadir (str dir "/data"))

(defn db
  "iu9-db"
  []
  (reify db/DB
    (setup! [_ _ node]
      (log/info node "installing iu9-db")
      (c/su (c/exec :mkdir :-p dir)
            (c/exec :mkdir datadir))
      (c/upload "resources/iu9-db" (str dir "/" binary))
      (c/exec :chmod :+x (str dir "/" binary))
      (cu/start-daemon!
       {:logfile logfile
        :pidfile pidfile
        :chdir   dir}
       binary
       :--logfile logfile
       :--datadir datadir)
      (Thread/sleep 1000))

    (teardown! [_ _ node]
      (log/info node "tearing down iu9-db")
      (cu/stop-daemon! binary pidfile)
      (c/su (c/exec :rm :-rf dir)))

    db/LogFiles
    (log-files [_ _ _]
      [logfile])))

(defn r   [_ _] {:type :invoke, :f :read, :value nil})
(defn w   [_ _] {:type :invoke, :f :write, :value (str (rand-int 5))})

(defrecord Client [conn]
  client/Client
  (open! [this test node]
    ;; (assoc this :conn @(grpc.http2/connect {:uri "http://localhost:50051"}))
    this)

  (setup! [this test])

  (invoke! [_ test op]
    ;; (case (:f op)
    ;;   :read (assoc op :type :ok, :value (dbclient/Get conn {:key "key"})))
    )

  (teardown! [this test])

  (close! [_ test]))

(defn simple-test
  "Given an options map from the command line runner (e.g. :nodes, :ssh,
  :concurrency, ...), constructs a test map."
  [opts]
  (merge tests/noop-test
         opts
         {:name "simple-test"
          :nodes ["n1.incus"]
          :os ubuntu/os
          :db (db)
          :client (Client. nil)
          :generator (->> r
                          (gen/stagger 1)
                          (gen/nemesis nil)
                          (gen/time-limit 15))
          :pure-generators true
          }))

(def cli-opts
  "Additional command line options."
  [])

(defn -main
  "Handles command line arguments. Can either run a test, or a web server for
  browsing results."
  [& args]
  (cli/run! (merge (cli/single-test-cmd {:test-fn simple-test
                                         :opt-spec cli-opts})
                   (cli/serve-cmd))
            args))
