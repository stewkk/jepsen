(ns jepsen.grpc.client
  (:import [iu9db DbGrpc]))

(def client (iu9db.DbGrpc/newBlockingStub
               (-> (io.grpc.ManagedChannelBuilder/forAddress "localhost" (int 50051))
                   (.usePlaintext)
                   .build)))

(defn get-request [key]
  (println
   (.getValue
    (.get client (-> (iu9db.Api$KeyRequest/newBuilder)
                                   (.setKey key)
                                   .build)))))
