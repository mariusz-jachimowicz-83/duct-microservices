(ns duct-microservices.module-test
  (:require
    [clojure.pprint]
    [clojure.test :refer :all]
    [duct.core      :as duct]
    [integrant.core :as ig]
    [duct-microservices.main]))

(duct/load-hierarchy)

(derive :duct.logger/fake :duct/logger)

(def base-config
  {:duct.logger/fake {}
   [:duct.server.http/jetty :system/s1] {:port 3005}
   [:duct.server.http/jetty :system/s2] {:port 3006}
   :duct.module.web/microservices {}
   [:duct.microservice/api :system/s1] {}
   [:duct.microservice/web :system/s2] {}})

(deftest configuration-test
  (testing ""
    (println "== testing")
    (let [system (-> base-config
                     duct/prep
                     #_ig/init)]
      
      (clojure.pprint/pprint system)
      #_(ig/halt! system))))
