(ns jepsen.grpc.client
  (:import [iu9db DbGrpc]))

(def client (iu9db.DbGrpc/newBlockingStub
               (-> (io.grpc.ManagedChannelBuilder/forAddress "n1.incus" (int 50051))
                   (.usePlaintext)
                   .build)))

(defn do-get [key]
  (.getValue
   (.get client (-> (iu9db.Api$KeyRequest/newBuilder)
                    (.setKey key)
                    .build))))

(defn do-insert [key value]
  (.insert client (-> (iu9db.Api$KeyValueRequest/newBuilder)
                      (.setKey key)
                      (.setValue value)
                      .build)))
