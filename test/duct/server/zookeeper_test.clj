(ns duct.server.zookeeper-test
  (:require
    [clojure.test :refer :all]
    [clj-http.client :as http]
    [duct.core       :as duct]
    [duct.logger     :as logger]
    [integrant.core  :as ig]
    [fipp.edn        :refer [pprint]]))

(duct/load-hierarchy)

(derive :duct.logger/fake :duct/logger)

(defrecord TestLogger []
  logger/Logger
  (-log [_ level ns-str file line id event data]))

;; fake logger initialization
;; we don't need whole logger subsystem
(defmethod ig/init-key :duct/logger [_ config] (->TestLogger))

(def base-config
  {:duct/logger (->TestLogger)

   :duct.server.zookeeper/embed
   {:logger (->TestLogger)
    :port 2188
    :address "127.0.0.1:2188"
    :reconnect-retries 2
    :embed? true}})

(deftest zookeeper-test
  (testing "launch embed Zookeeper server for development"
    (let [system (-> base-config
                     duct/prep
                     ig/init)]
      (try
        (finally
          (ig/halt! system))))))


