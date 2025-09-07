(ns jepsen.iu9db
  (:require [clojure.tools.logging :as log]
            [jepsen [cli :as cli]
             [tests :as tests]
             [db :as db]
             [client :as client]
             [generator :as gen]
             [independent :as independent]
             [checker :as checker]]
            [jepsen.os.ubuntu :as ubuntu]
            [jepsen.control.util :as cu]
            [jepsen.control :as c]
            [jepsen.grpc.client :as dbclient]
            [knossos.model :as model]
            [jepsen.checker.timeline :as timeline])
  (:import [io.grpc StatusRuntimeException Status$Code]))

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
       :--datadir datadir)
       :--debug_level 5
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
    this)

  (setup! [this test])

  (invoke! [_ test op]
    (let [[k v] (:value op)]
      (case (:f op)
        :read (try
                (assoc op :type :ok, :value (independent/tuple k (dbclient/do-get (str k))))
                (catch StatusRuntimeException e
                  (if (= (.getCode (.getStatus e)) Status$Code/NOT_FOUND)
                    (assoc op :type :ok, :value (independent/tuple k "notfound"))
                    (assoc op
                           :type  :fail,
                           :error :unknown))))
        :write (do (dbclient/do-insert (str k) v)
                   (assoc op :type :ok)))))

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
          :generator (->> (independent/concurrent-generator
                           10
                           (range)
                           (fn [k]
                             (->> (gen/mix [r w])
                                  (gen/stagger 1/50)
                                  (gen/limit 100))))
                          (gen/nemesis nil)
                          (gen/time-limit 15))
          :checker  (checker/compose
                     {:perf  (checker/perf)
                      :indep (independent/checker
                              (checker/compose
                               {:linear   (checker/linearizable {:model (model/register "notfound")
                                                                 :algorithm :linear})
                                :timeline (timeline/html)}))})
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
