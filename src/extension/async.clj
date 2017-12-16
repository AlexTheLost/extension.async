(ns extension.async
  (:require [clojure.core.async :as async]
            [clojure.core.async.impl.ioc-macros :as ioc]
            [clojure.core.async.impl.dispatch :as dispatch]
            ;; DEV
            [clojure.pprint :refer [pprint]]
            )
  (:import (java.util.concurrent ThreadFactory ExecutorService Executors)
           ))



(defrecord ResultException [^Exception exception])


(defmacro go?
  [& body]
  (let [crossing-env (zipmap (keys &env) (repeatedly gensym))]
    `(let [c# (async/chan 1)
           captured-bindings# (clojure.lang.Var/getThreadBindingFrame)]

       (dispatch/run
         (^:once fn* []
                 (let [~@(mapcat (fn [[l sym]] [sym `(^:once fn* [] ~(vary-meta l dissoc :tag))]) crossing-env)
                       f# ~(ioc/state-machine `(try
                                                 ~@body
                                                 (catch Exception exc#
                                                   (ResultException. exc#)
                                                   ))
                                              1
                                              [crossing-env &env]
                                              ioc/async-custom-terminators)
                       state# (-> (f#)
                                  (ioc/aset-all! ioc/USER-START-IDX c#
                                                 ioc/BINDINGS-IDX captured-bindings#))]
                   (ioc/run-state-machine-wrapped state#))))
       c#)))


(defmacro <?
  "Retrieve next message from ch, if message is exception then throw new exception with cause containing the exception retrieval from message."
  [ch]
  `(let [message# (async/<! ~ch)]
     (if (instance? ResultException message#)
       ;;(throw (RuntimeException. (:exception message#)))
       (throw (:exception message#))
       message#)))


(defn <??
  "Retrieve next message from ch, if message is exception then throw new exception with cause containing the exception retrieval from message."
  [ch]
  (let [message (async/<!! ch)]
    (if (instance? ResultException message)
      ;;(throw (RuntimeException. (:exception message)))
      (throw (:exception message))
      message)))



(def ^:private thread-pool-counter (atom 0))



(defn new-fixed-thread-pool
  ([thread-count thread-pool-name daemon?]
   (Executors/newFixedThreadPool
     thread-count
     (let [counter (atom 0)]
       (reify
         ThreadFactory
         (newThread [this runnable]
                    (doto (Thread. runnable)
                      (.setName (str thread-pool-name "-" (swap! counter inc)))
                      (.setDaemon daemon?)))))))
  ([thread-count thread-pool-name]
   (new-fixed-thread-pool thread-count thread-pool-name true)
   )
  ([thread-count]
   (new-fixed-thread-pool thread-count (str "esync-thread-pool-" (swap! thread-pool-counter inc)) true)))


(defn thread-call
  "Like 'core.async' 'thread-call' but accept thread pool on which will be calculated body."
  [^ExecutorService thread-pool f]
  (let [c (async/chan 1)]
    (let [binds (clojure.lang.Var/getThreadBindingFrame)]
      (.execute
        thread-pool
        (fn []
          (clojure.lang.Var/resetThreadBindingFrame binds)
          (try
            (let [ret (f)]
              (when-not (nil? ret)
                (async/>!! c ret)))
            (finally
              (async/close! c))))))
    c))


(defmacro thread
  "Like 'core.async' 'thread' but accept thread pool on which will be calculated body."
  [& body]
  `(thread-call (^:once fn* [] ~@body)))

