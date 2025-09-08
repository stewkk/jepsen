(ns jepsen.workloads.common
  (:require [clojure.tools.logging :as log]
            [jepsen
             [db :as db]]
            [jepsen.control.util :as cu]
            [jepsen.control :as c]))

(def dir "/opt")
(def binary "iu9-db")
(def pidfile (str dir "/iu9db.pid"))
(def logfile (str dir "/iu9db.log"))
(def datadir (str dir "/data"))

(defn iu9db
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
