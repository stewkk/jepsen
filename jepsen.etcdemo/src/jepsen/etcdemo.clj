(ns jepsen.etcdemo
  (:require [clojure.tools.logging :refer [info]]
            [clojure.string :as str]
            [jepsen [cli :as cli]
             [control :as c]
             [db :as db]
             [tests :as tests]
             [generator :as gen]
             [client :as client]
             [checker :as checker]
             [nemesis :as nemesis]
             [independent :as independent]]
            [jepsen.checker.timeline :as timeline]
            [jepsen.control.util :as cu]
            [jepsen.os.debian :as debian]
            [verschlimmbesserung.core :as v]
            [slingshot.slingshot :refer [try+]]
            [knossos.model :as model]
            [jepsen.independent :as independent]
            [jepsen.checker :as checker]))

(def dir     "/opt/etcd")
(def binary "etcd")
(def logfile (str dir "/etcd.log"))
(def pidfile (str dir "/etcd.pid"))

(defn node-url
  "An HHTP url for connecting to a node on a particular port."
  [node port]
  (str "http://" node ":" port))

(defn peer-url
  "The HTTP url for other peers to talk to a node."
  [node]
  (node-url node 2380))

(defn client-url
  "The HTTP url clients use to talk to a node."
  [node]
  (node-url node 2379))

(defn initial-cluster
  "Constructs an initial cluster string for a test, like
  \"foo=foo:2380,bar=bar:2380,...\""
  [test]
  (->> (:nodes test)
       (map (fn [node]
              (str node "=" (peer-url node))))
       (str/join ",")))

(defn db
  "Etcd DB for a particular version."
  [version]
  (reify db/DB
    (setup! [_ test node]
      (info node "installing etcd" version)
      (c/su
       (let [url (str "https://storage.googleapis.com/etcd/" version
                      "/etcd-" version "-linux-amd64.tar.gz")]
         (cu/install-archive! url dir))
       (cu/start-daemon!
        {:logfile logfile
         :pidfile pidfile
         :chdir dir}
        binary
        :--log-output                   :stderr
        :--name                         (name node)
        :--listen-peer-urls             (peer-url   node)
        :--listen-client-urls           (client-url node)
        :--advertise-client-urls        (client-url node)
        :--initial-cluster-state        :new
        :--initial-advertise-peer-urls  (peer-url node)
        :--initial-cluster              (initial-cluster test))
       (Thread/sleep 1000)))

    (teardown! [_ _ node]
      (info node "tearing down etcd")
      (cu/stop-daemon! binary pidfile)
      (c/su (c/exec :rm :-rf dir)))

    db/LogFiles
    (log-files [_ _ _]
      [logfile])))

(defn r   [_ _] {:type :invoke, :f :read, :value nil})
(defn w   [_ _] {:type :invoke, :f :write, :value (rand-int 5)})
(defn cas [_ _] {:type :invoke, :f :cas, :value [(rand-int 5) (rand-int 5)]})

(defn parse-long-nil
  "Parses a string to a Long. Passes through `nil`."
  [s]
  (when s (parse-long s)))


(defrecord Client [conn]
  client/Client
  (open! [this _ node]
    (assoc this :conn (v/connect (client-url node)
                                 {:timeout 5000})))

  (setup! [_ _])

  (invoke! [_ test op]
    (let [[k v] (:value op)]
      (try+
       (case (:f op)
         :read (let [value (-> conn
                               (v/get k {:quorum? (:quorum test)})
                               parse-long-nil)]
                 (assoc op :type :ok , :value (independent/tuple k value)))
         :write (do (v/reset! conn k v)
                    (assoc op :type :ok))
         :cas (let [[old new] v]
                (assoc op :type (if (v/cas! conn k old new)
                                  :ok
                                  :fail))))
       #_{:clj-kondo/ignore [:unresolved-symbol]} ;; NOTE: lsp bug
       (catch [:errorCode 100] ex
         (assoc op :type :fail, :error :not-found))
       #_{:clj-kondo/ignore [:unresolved-symbol]} ;; NOTE: lsp bug
       (catch java.net.SocketTimeoutException e
         (assoc op
                :type  (if (= :read (:f op))
                         :fail
                         :info)
                :error :timeout)))))

  (teardown! [_ _])

  (close! [_ _]))

(defn etcd-test
  "Given an options map from the command line runner (e.g. :nodes, :ssh,
  :concurrency, ...), constructs a test map."
  [opts]
  (let [quorum (boolean (:quorum opts))]

    (merge tests/noop-test
           {:name (str "etcd q=" quorum)
            :os debian/os
            :db (db "v3.1.5")
            :pure-generators true
            :quorum quorum
            :client (Client. nil)
            :checker (checker/compose
                      {:perf   (checker/perf)
                       :indep (independent/checker
                               (checker/compose
                                {:linear (checker/linearizable {:model     (model/cas-register)
                                                                :algorithm :linear})
                                 :timeline (timeline/html)}))})
            :nemesis (nemesis/partition-random-halves)
            :generator (->> (independent/concurrent-generator
                             10
                             (range)
                             (fn [_]
                               (->> (gen/mix [r w cas])
                                    (gen/stagger 1/25)
                                    (gen/limit 100))))
                            (gen/nemesis
                             (cycle [(gen/sleep 5)
                                     {:type :info, :f :start}
                                     (gen/sleep 5)
                                     {:type :info, :f :stop}]))
                            (gen/time-limit (:time-limit opts)))}
           opts)))

(def cli-opts
  "Additional command line options."
    [["-q" "--quorum" "Use quorum reads, instead of reading from any primary."]])

(defn -main
  "Handles command line arguments. Can either run a test, or a web server for
  browsing results."
  [& args]
  (cli/run! (merge (cli/single-test-cmd {:test-fn etcd-test
                                         :opt-spec cli-opts})
                   (cli/serve-cmd))
            args))
