(defproject jepsen.iu9db "0.1.0-SNAPSHOT"
  :description "A Jepsen test for iu9-distributed-db"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [jepsen "0.3.10-SNAPSHOT"]
                 [io.grpc/grpc-core "1.75.0"]
                 [io.netty/netty-codec-http2 "4.2.5.Final"]
                 [com.google.protobuf/protobuf-java "4.32.0"]
                 [javax.annotation/javax.annotation-api "1.3.2"]
                 [io.grpc/grpc-netty "1.75.0"
                  :exclusions [io.grpc/grpc-core
                               io.netty/netty-codec-http2]]
                 [io.grpc/grpc-protobuf "1.75.0"]
                 [io.grpc/grpc-stub "1.75.0"]]
  :java-source-paths
  ["target/generated-sources/protobuf"]
  :repl-options {:init-ns jepsen.iu9db}
  :main jepsen.iu9db)
