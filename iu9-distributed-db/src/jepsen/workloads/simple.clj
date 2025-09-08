(ns jepsen.workloads.simple
  (:require
            [jepsen
             [tests :as tests]
             [client :as client]
             [generator :as gen]
             [independent :as independent]
             [checker :as checker]]
            [jepsen.os.ubuntu :as ubuntu]
            [knossos.model :as model]
            [jepsen.checker.timeline :as timeline]
            [jepsen.grpc.client :as dbclient]
            [jepsen.workloads.common :as common])
  (:import [io.grpc StatusRuntimeException Status$Code]))

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
          :db (common/iu9db)
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
