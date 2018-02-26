(ns duct.module.microservices
  (:require
    [clojure.pprint]
    [clojure.string  :as string]
    [clojure.java.io :as io]
    [duct.core       :as duct]
    [duct.core.env   :as env]
    [duct.core.merge :as merge]
    [integrant.core  :as ig]
    [medley.core     :as m]))

(def ^:private server-port
  (env/env '["PORT" Int :or 3000]))

(defn- get-environment [config options]
  (:environment options (:duct.core/environment config :production)))

(defn- get-project-ns [config options]
  (:project-ns options (:duct.core/project-ns config)))

(defn- name-to-path [sym]
  (-> sym name (string/replace "-" "_") (string/replace "." "/")))

(defn- derived-key [m k default]
  (if-let [kv (ig/find-derived-1 m k)] (key kv) default))

;; TODO: fix - allow to launch http-kit
#_(defn- http-server-key [config ms-id-key]
  (derived-key config :duct.server/http [:duct.server.http/jetty ms-id-key]))

(defn- server-config [config ms-id-key]
  {[:duct.server.http/jetty ms-id-key] {:port (merge/displace server-port)}})

(defn- router-config [config ms-id-key]
  (if-not (ig/find-derived-1 config [:duct/router ms-id-key])
    {[:duct.router/cascading ms-id-key] []}
    {}))

(defn- logging-config [ms-id-key]
  {[:duct.middleware.web/log-requests ms-id-key] {:logger (ig/ref :duct/logger)}
   [:duct.middleware.web/log-errors   ms-id-key] {:logger (ig/ref :duct/logger)}
   [:duct.core/handler ms-id-key]
   {:middleware ^:distinct [(ig/ref [:duct.middleware.web/log-requests ms-id-key])
                            (ig/ref [:duct.middleware.web/log-errors   ms-id-key])]}})

(defn- error-configs [ms-id-key]
  {:production
   {[:duct.core/handler ms-id-key]
    {:middleware ^:distinct [(ig/ref [:duct.middleware.web/hide-errors ms-id-key])]}}

   :development
   {[:duct.core/handler ms-id-key]
    {:middleware ^:distinct [(ig/ref [:duct.middleware.web/stacktrace ms-id-key])]}}})

(defn- common-config [ms-id-key]
  {[:duct.middleware.web/not-found ms-id-key]
   {:error-handler (merge/displace (ig/ref [:duct.handler.static/not-found ms-id-key]))}

   [:duct.middleware.web/hide-errors ms-id-key]
   {:error-handler (merge/displace (ig/ref [:duct.handler.static/internal-server-error ms-id-key]))}

   [:duct.middleware.web/stacktrace ms-id-key] {}
   [:duct.core/handler ms-id-key] {:router  (merge/displace (ig/ref [:duct/router       ms-id-key]))}
   [:duct.server/http  ms-id-key] {:handler (merge/displace (ig/ref [:duct.core/handler ms-id-key]))
                                   :logger  (merge/displace (ig/ref :duct/logger))}})

(defn- plaintext-response [text]
  ^:demote {:headers {"Content-Type" "text/plain; charset=UTF-8"}, :body text})

(defn- html-response [html]
  ^:demote {:headers {"Content-Type" "text/html; charset=UTF-8"}, :body html})

(def ^:private base-ring-defaults
  ^:demote {:params    {:urlencoded true, :keywordize true}
            :responses {:not-modified-responses true
                        :absolute-redirects true
                        :content-types true
                        :default-charset "utf-8"}})

(defn- base-config [ms-id-key]
  {[:duct.handler.static/bad-request           ms-id-key] (plaintext-response "Bad Request")
   [:duct.handler.static/not-found             ms-id-key] (plaintext-response "Not Found")
   [:duct.handler.static/method-not-allowed    ms-id-key] (plaintext-response "Method Not Allowed")
   [:duct.handler.static/internal-server-error ms-id-key] (plaintext-response "Internal Server Error")
   [:duct.middleware.web/defaults              ms-id-key] base-ring-defaults

   [:duct.core/handler ms-id-key]
   {:middleware ^:distinct [(ig/ref [:duct.middleware.web/not-found ms-id-key])
                            (ig/ref [:duct.middleware.web/defaults  ms-id-key])]}})

(defn api-config [ms-id-key]
  {[:duct.handler.static/bad-request        ms-id-key] {:body ^:displace {:error :bad-request}}
   [:duct.handler.static/not-found          ms-id-key] {:body ^:displace {:error :not-found}}
   [:duct.handler.static/method-not-allowed ms-id-key] {:body ^:displace {:error :method-not-allowed}}

   [:duct.handler.static/internal-server-error ms-id-key]
   {:body ^:displace {:error :internal-server-error}}

   [:duct.middleware.web/format ms-id-key]   {}
   [:duct.middleware.web/defaults ms-id-key] base-ring-defaults

   [:duct.core/handler ms-id-key]
   {:middleware ^:distinct [(ig/ref [:duct.middleware.web/not-found ms-id-key])
                            (ig/ref [:duct.middleware.web/format    ms-id-key])
                            (ig/ref [:duct.middleware.web/defaults  ms-id-key])]}})

(def ^:private error-400 (io/resource "duct/module/web/errors/400.html"))
(def ^:private error-404 (io/resource "duct/module/web/errors/404.html"))
(def ^:private error-405 (io/resource "duct/module/web/errors/405.html"))
(def ^:private error-500 (io/resource "duct/module/web/errors/500.html"))

(defn- site-ring-defaults [project-ns]
  ^:demote {:params    {:urlencoded true, :multipart true, :nested true, :keywordize true}
            :cookies   true
            :session   {:flash true, :cookie-attrs {:http-only true, :same-site :strict}}
            :security  {:anti-forgery         true
                        :xss-protection       {:enable? true, :mode :block}
                        :frame-options        :sameorigin
                        :content-type-options :nosniff}
            :static    {:resources ["duct/module/web/public"
                                    (str (name-to-path project-ns) "/public")]}
            :responses {:not-modified-responses true
                        :absolute-redirects     true
                        :content-types          true
                        :default-charset        "utf-8"}})

(defn- site-config [project-ns ms-id-key]
  {[:duct.handler.static/bad-request           ms-id-key] (html-response error-400)
   [:duct.handler.static/not-found             ms-id-key] (html-response error-404)
   [:duct.handler.static/method-not-allowed    ms-id-key] (html-response error-405)
   [:duct.handler.static/internal-server-error ms-id-key] (html-response error-500)
   [:duct.middleware.web/webjars  ms-id-key] {}
   [:duct.middleware.web/defaults ms-id-key] (site-ring-defaults project-ns)

   [:duct.core/handler ms-id-key]
   {:middleware ^:distinct [(ig/ref [:duct.middleware.web/not-found ms-id-key])
                            (ig/ref [:duct.middleware.web/webjars   ms-id-key])
                            (ig/ref [:duct.middleware.web/defaults  ms-id-key])]}})

(defn- apply-web-module [config options module-config ms-id-key]
  (duct/merge-configs config
                      (server-config config ms-id-key)
                      (router-config config ms-id-key)
                      (common-config ms-id-key)
                      module-config
                      (logging-config ms-id-key)
                      ((error-configs ms-id-key) (get-environment config options))))

(defn apply-microservice [config options [ms-type ms-id-key]]
  (let [module-cfg (case ms-type
                     :duct.microservice/site
                     (site-config (get-project-ns config options) ms-id-key)

                     :duct.microservice/api
                     (api-config ms-id-key)

                     :duct.microservice/web
                     (base-config ms-id-key))]
    (apply-web-module config options module-cfg ms-id-key)))

(defn microservice-cfgs [config]
  (ig/find-derived config :duct/microservice))

(defmethod ig/init-key :duct.module/microservices [key options]
  {:fn (fn [config]
         (let [ms-keys (->> config
                            microservice-cfgs
                            (into (sorted-map))
                            keys)
               cfg (->> ms-keys
                        (map (partial apply-microservice config options))
                        (apply duct/merge-configs))]
           cfg))})

(defmethod ig/init-key :duct.microservice/web [key options]
  (do
    (println "== init microservice " key)
    options))

(defmethod ig/init-key :duct.microservice/api [key options]
  (do
    (println "== init microservice " key)
    options))

(defmethod ig/init-key :duct.microservice/site [key options]
  (do
    (println "== init microservice " key)
    options))
