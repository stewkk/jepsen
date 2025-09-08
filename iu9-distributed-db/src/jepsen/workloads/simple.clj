(ns jepsen.workloads.simple
  (:require [clojure.tools.logging :as log]
            [jepsen
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

;; (defn zk-node-ids
;;   "Returns a map of node names to node ids."
;;   [test]
;;   (->> test
;;        :nodes
;;        (map-indexed (fn [i node] [node i]))
;;        (into {})))

;; (defn zk-node-id
;;   "Given a test and a node name from that test, returns the ID for that node."
;;   [test node]
;;   ((zk-node-ids test) node))

;; (defn zoo-cfg-servers
;;   "Constructs a zoo.cfg fragment for servers."
;;   [test]
;;   (->> (zk-node-ids test)
;;        (map (fn [[node id]]
;;               (str "server." id "=" (name node) ":2888:3888")))
;;        (str/join "\n")))

;; (defn zk
;;   "Zookeeper DB for a particular version."
;;   [version]
;;   (reify db/DB
;;     (setup! [_ test node]
;;       (c/su
;;         (info node "installing ZK" version)
;;         (debian/install {:zookeeper version
;;                          :zookeeper-bin version
;;                          :zookeeperd version})

;;         (c/exec :echo (zk-node-id test node) :> "/etc/zookeeper/conf/myid")

;;         (c/exec :echo (str (slurp (io/resource "zoo.cfg"))
;;                            "\n"
;;                            (zoo-cfg-servers test))
;;                 :> "/etc/zookeeper/conf/zoo.cfg")

;;         (info node "ZK restarting")
;;         (c/exec :service :zookeeper :stop)
;;         (c/exec :service :zookeeper :start) ; for some reason, the restart often fails.
;;         (info node "ZK ready")))

;;     (teardown! [_ test node]
;;       (info node "tearing down ZK")
;;       (c/su
;;         (c/exec :service :zookeeper :stop) ; we must first comment this line to let the node install zk.
;;         (c/exec :rm :-rf
;;                 (c/lit "/var/lib/zookeeper/version-*")
;;                 (c/lit "/var/log/zookeeper/*"))))

;;     db/LogFiles
;;     (log-files [_ test node]
;;       ["/var/log/zookeeper/zookeeper.log"])))

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
