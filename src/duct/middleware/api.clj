(ns duct.middleware.api
  (:require
    [clojure.java.io     :as io]
    [integrant.core      :as ig]
    [ring.util.response  :as resp]))

(defn- get-request? [request]
  (#{:head :get} (:request-method request)))

(defn wrap-health
  [handler]
  (fn
    ([request]
     (if (get-request? request)
       (resp/response {:health :up})
       (handler request)))
    ([request respond raise]
     (if (get-request? request)
       (respond (resp/response {:health :up}))
       (handler request respond raise)))))

(defmethod ig/init-key :duct.middleware.api/health [_ options]
  #(wrap-health %))

#_(defmethod ig/init-key :duct.middleware.api/status [_ options]
  #(wrap-status %))
