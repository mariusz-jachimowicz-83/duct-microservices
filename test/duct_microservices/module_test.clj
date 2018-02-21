(ns duct-microservices.module-test
  (:require
    [clj-http.client :as http]
    [clojure.pprint]
    [clojure.test :refer :all]
    [duct.core      :as duct]
    [duct.logger    :as logger]
    [integrant.core :as ig]
    [duct-microservices.main]))

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
   [:duct.server.http/jetty :system/s1] {:port 3005}
   [:duct.server.http/jetty :system/s2] {:port 3006}

   :duct.module.web/microservices {}
   [:duct.microservice/api :system/s1] {}
   [:duct.microservice/web :system/s2] {}})

(deftest configuration-test
  (testing ""
    (let [response-1 {:status 200 :headers {} :body "microservice-1"}
          handler-1  (constantly response-1)
          response-2 {:status 200 :headers {} :body "microservice-2"}
          handler-2  (constantly response-2)
          system (-> base-config
                     duct/prep
                     (assoc-in [[:duct.server.http/jetty :system/s1] :handler] handler-1)
                     (assoc-in [[:duct.server.http/jetty :system/s2] :handler] handler-2)
                     ig/init)]
      (try
        #_(clojure.pprint/pprint system)
        (let [resp-1 (http/get "http://127.0.0.1:3005/")
              resp-2 (http/get "http://127.0.0.1:3006/")]
          (is (= (:status resp-1) 200))
          (is (= (:body resp-1) "microservice-1"))
          (is (= (:status resp-2) 200))
          (is (= (:body resp-2) "microservice-2")))
        (finally
          (ig/halt! system))))))
