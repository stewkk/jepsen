(ns jepsen.grpc.client
  (:import [iu9db DbGrpc]
           [io.grpc StatusRuntimeException]))

(def client (iu9db.DbGrpc/newBlockingStub
               (-> (io.grpc.ManagedChannelBuilder/forAddress "localhost" (int 50051))
                   (.usePlaintext)
                   .build)))

(defn do-get [key]
   (try
     (.getValue
      (.get client (-> (iu9db.Api$KeyRequest/newBuilder)
                       (.setKey key)
                       .build)))
     (catch StatusRuntimeException e
       (.getStatus e))))

(defn do-insert [key value]
  (try (.insert client (-> (iu9db.Api$KeyValueRequest/newBuilder)
                           (.setKey key)
                           (.setValue value)
                           .build))
       (catch StatusRuntimeException e
         (.getStatus e))))
