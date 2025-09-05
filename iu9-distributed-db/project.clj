(defproject jepsen.etcdemo "0.1.0-SNAPSHOT"
  :description "A Jepsen test for etcd"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [jepsen "0.3.10-SNAPSHOT"]
                 [verschlimmbesserung "0.1.3"]]
  :repl-options {:init-ns jepsen.etcdemo}
  :main jepsen.etcdemo)
