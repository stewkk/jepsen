(ns jepsen.etcdemo
  (:require [clojure.tools.logging :refer :all]
            [clojure.string :as str]
            [jepsen [cli :as cli]
             [control :as c]
             [db :as db]
             [tests :as tests]
             [generator :as gen]
             [client :as client]]
            [jepsen.control.util :as cu]
            [jepsen.os.debian :as debian]
            [verschlimmbesserung.core :as v]))

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
       ;; FIXME: wait for healthcheck
       (Thread/sleep 10000)))

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
  (open! [this test node]
    (assoc this :conn (v/connect (client-url node)
                                 {:timeout 5000})))

  (setup! [this test])

  (invoke! [_ test op]
    (case (:f op)
      :read (assoc op :type :ok , :value (->> (v/get conn "foo")
                                              parse-long-nil))
      :write (do (v/reset! conn "foo" (:value op))
                 (assoc op :type :ok))))

  (teardown! [this test])

  (close! [_ test]))

(defn etcd-test
  "Given an options map from the command line runner (e.g. :nodes, :ssh,
  :concurrency, ...), constructs a test map."
  [opts]
  (merge tests/noop-test
         {:name "etcd"
          :os debian/os
          :db (db "v3.1.5")
          :pure-generators true
          :client (Client. nil)
          :generator (->> (gen/mix [r w])
                          (gen/stagger 1)
                          (gen/nemesis nil)
                          (gen/time-limit 15))}
         opts))

(defn -main
  "Handles command line arguments. Can either run a test, or a web server for
  browsing results."
  [& args]
  (cli/run! (merge (cli/single-test-cmd {:test-fn etcd-test})
                   (cli/serve-cmd))
            args))
