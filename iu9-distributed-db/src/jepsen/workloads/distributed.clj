(ns jepsen.workloads.distributed
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [clojure.java.io :as io]
            [jepsen
             [tests :as tests]
             [db :as db]
             [generator :as gen]
             [control :as c]
             [independent :as independent]
             [checker :as checker]]
            [jepsen.control.util :as cu]
            [knossos.model :as model]
            [jepsen.checker.timeline :as timeline]
            [jepsen.os.debian :as debian]
            [jepsen.os.ubuntu :as ubuntu]
            [jepsen.workloads.common :as common]))


(defn zk-node-ids
  "Returns a map of node names to node ids."
  [_]
  (->> {:nodes ["n4.incus" "n5.incus" "n6.incus"]}
       :nodes
       (map-indexed (fn [i node] [node i]))
       (into {})))

(defn zk-node-id
  "Given a test and a node name from that test, returns the ID for that node."
  [test node]
  ((zk-node-ids test) node))

(defn zoo-cfg-servers
  "Constructs a zoo.cfg fragment for servers."
  [_]
  (->> (zk-node-ids {:nodes ["n4.incus" "n5.incus" "n6.incus"]})
       (map (fn [[node id]]
              (str "server." id "=" (name node) ":2888:3888")))
       (str/join "\n")))

(defn zk
  []
  (reify db/DB
    (setup! [_ test node]
      (let [version "3.8.4"]
        (c/su

         (log/info node "installing ZK" version)

         (debian/install {
                          :openjdk-17-jdk-headless "17.0.16+8~us1-0ubuntu1~25.04.1"
                          })

         (c/exec :mkdir :-p "/opt/zookeeper")

         (cu/install-archive! "https://dlcdn.apache.org/zookeeper/zookeeper-3.8.4/apache-zookeeper-3.8.4-bin.tar.gz"
                              "/opt/zookeeper")

         (c/exec :mkdir :-p "/var/lib/zookeeper /var/log/zookeeper")

         (c/exec :echo (zk-node-id test node) :> "/etc/zookeeper/conf/myid")

         (c/exec :echo (str (slurp (io/resource "zoo.cfg"))
                            "\n"
                            (zoo-cfg-servers test))
                 :> "/opt/zookeeper/conf/zoo.cfg")

         (log/info node "ZK starting")
         (cu/start-daemon!
          {:logfile "/var/log/zookeeper/zookeeper.log"
           :pidfile "/zookeeper.pid"
           :chdir "/"}
          "/opt/zookeeper/bin/zkServer.sh"
          :start-foreground)
         (Thread/sleep 10000)
         (log/info node "ZK ready"))))

    (teardown! [_ test node]
      (log/info node "tearing down ZK")
      (c/su
       (c/exec :rm :-rf
               (c/lit "/var/lib/zookeeper/version-*")
               (c/lit "/var/log/zookeeper/*"))))

    db/LogFiles
    (log-files [_ test node]
      ["/var/log/zookeeper/zookeeper.log"])))

(defn iu9db-cluster
  []
  (reify db/DB
    (setup! [_ test node]
      (if (some #(= node %) ["n1.incus" "n2.incus" "n3.incus"])
        (db/setup! (common/iu9db "n4.incus:2181,n5.incus:2181,n6.incus:2181") test node)
        (db/setup! (zk) test node)))

    (teardown! [_ test node]
      (if (some #(= node %) ["n1.incus" "n2.incus" "n3.incus"])
        (db/teardown! (common/iu9db "n4.incus:2181,n5.incus:2181,n6.incus:2181") test node)
        (db/teardown! (zk) test node)))

    db/LogFiles
    (log-files [_ test node]
      (if (some #(= node %) ["n1.incus" "n2.incus" "n3.incus"])
        (db/log-files (common/iu9db "n4.incus:2181,n5.incus:2181,n6.incus:2181") test node)
        (db/log-files (zk) test node)))))


(defn distributed-test
  "Given an options map from the command line runner (e.g. :nodes, :ssh,
  :concurrency, ...), constructs a test map."
  [opts]
  (merge tests/noop-test
         opts
         {:name "distributed-test"
          :nodes ["n1.incus" "n2.incus" "n3.incus" "n4.incus" "n5.incus" "n6.incus"]
          :os ubuntu/os
          :db (iu9db-cluster)
          ;; :client (Client. nil)
          ;; :generator (->> (independent/concurrent-generator
          ;;                  10
          ;;                  (range)
          ;;                  (fn [k]
          ;;                    (->> (gen/mix [r w])
          ;;                         (gen/stagger 1/50)
          ;;                         (gen/limit 100))))
          ;;                 (gen/nemesis nil)
          ;;                 (gen/time-limit 15))
          ;; :checker  (checker/compose
          ;;            {:perf  (checker/perf)
          ;;             :indep (independent/checker
          ;;                     (checker/compose
          ;;                      {:linear   (checker/linearizable {:model (model/register "notfound")
          ;;                                                        :algorithm :linear})
          ;;                       :timeline (timeline/html)}))})
          :pure-generators true
          }))
