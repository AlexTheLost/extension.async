(ns extension.async-test
  (:require [clojure.test :refer :all]
            [extension.async :refer :all]
            [clojure.core.async :as async]
            [clojure.pprint :refer [pprint]]
            ))



;; (def thread-pool
;;   (new-fixed-thread-pool 2))


;; (.execute thread-pool (fn []
;;                         (println (.getId (Thread/currentThread)))
;;                         ))


;; (async/go
;;   (let [result (async/<! (thread-call
;;                            thread-pool
;;                            (fn []
;;                              (let [thread-id (.getId (Thread/currentThread))]
;;                                (println thread-id)
;;                                thread-id))))]
;;     (println result)))



(go?
  (<! (async/chan))
  (throw (RuntimeException.)))


(go? (throw (RuntimeException.)))


(async/go
  (prn 1)
  (prn (async/<! (go?
                   (try
                     (let [ch (async/chan 1)]
                       (async/>! ch 1)
                       (async/<! ch))
                     (catch Exception exc
                       (prn exc)))

                   ;;(throw (RuntimeException.))
                   (/ 1 0)
                   )))
  (prn 2))



