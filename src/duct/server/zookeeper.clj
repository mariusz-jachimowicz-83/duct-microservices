(ns duct.server.zookeeper
  (:require
    [clojure.core.async :refer [go-loop pub sub go >! <! chan >!! <!! close! thread alts!! offer!]]
    [integrant.core      :as ig]
    [duct.logger :refer [log]]
    [duct.server.curator :as zk])
  (:import [org.apache.curator.test TestingServer]
           [org.apache.log4j BasicConfigurator]
           [org.apache.curator.framework CuratorFramework]
           [org.apache.zookeeper KeeperException$NoNodeException
            KeeperException$NodeExistsException KeeperException$BadVersionException]))

(defmethod ig/init-key ::embed [_ {:keys [logger port address reconnect-retries embed?] :as opts}]
  (let [logger (atom logger)
        _ (log @logger :report ::starting-server port)
        max-retries  (or reconnect-retries 24)
        restarter-ch (chan 1)
        failure-ch   (chan 1)
        server (when embed? (TestingServer. (int port)))
        conn (zk/connect-n-retry address)
        notify-restart (zk/notify-restarter conn restarter-ch)
        restarter (zk/try-reconnect-or-die conn restarter-ch failure-ch max-retries)]
    {:logger       logger
     :restarter-ch restarter-ch
     :server       server
     :conn         conn
     :notify-restart notify-restart
     :restarter      restarter}))

(defmethod ig/halt-key! ::embed [_ {:keys [logger server conn restarter notify-restart restarter-ch failure-ch]}]
  (log @logger :report ::stopping-server)
  (when restarter-ch (close! restarter-ch))
  (when restarter (future-cancel restarter))
  (when notify-restart (zk/remove-conn-watcher conn notify-restart))
  (when (.. ^CuratorFramework conn isStarted)
    (zk/close conn))
  (when server (.close ^TestingServer server)))

#_(defmethod ig/init-key ::connection [_ {:keys [logger port address] :as opts}]
  (let [logger (atom logger)]
    (log @logger :report ::starting-server-connection)
    {:logger  logger
     :address address
     :conn    (zk/connect address)}))

#_(defmethod ig/halt-key! ::connection [_ {:keys [logger conn address]}]
  (log @logger :report ::stopping-server-connection address)
  (zk/close conn))
