(ns duct.middleware.api-test
  (:require
    [clojure.test :refer :all]
    [clj-http.client :as http]
    [compojure.core  :as compojure]
    [duct.core       :as duct]
    [duct.logger     :as logger]
    [integrant.core  :as ig]
    [ring.mock.request :as mock]
    [fipp.edn        :refer [pprint]]
    [duct.middleware.api :refer :all]))

(deftest wrap-health-test
  (let [response {:status 200, :headers {}, :body {:a 1 :b 2}}
        health   {:health :up}
        response-health   {:status 200, :headers {}, :body health}]

    (testing "synchronous"
      (let [handler (wrap-health (constantly response))]
        (is (= response-health
               (handler (mock/request :get "/health"))))
        (is (= response
               (handler (mock/request :get "/example"))))))

    (testing "asynchronous"
      (let [handler (wrap-health (fn [_ respond _]
                                   (respond response)))]

        (testing "health route"
          (let [respond (promise)
                raise   (promise)]
            (handler (mock/request :get "/health") respond raise)
            (is (not (realized? raise)))
            (is (= response-health @respond))))

        (testing "other route"
          (let [respond (promise)
                raise   (promise)]
            (handler (mock/request :get "/example") respond raise)
            (is (not (realized? raise)))
            (is (= response @respond))))))))
