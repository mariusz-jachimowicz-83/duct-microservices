(ns duct.server.curator
  (:require
    [clojure.core.async :refer [go-loop pub sub go >! <! chan >!! <!! close! thread alts!! offer!]])
  (:import
    [java.util.concurrent TimeUnit]
    [org.apache.zookeeper CreateMode]
    [org.apache.zookeeper
     KeeperException
     KeeperException$NoNodeException
     KeeperException$NodeExistsException
     KeeperException$Code
     Watcher]
    [org.apache.curator.test TestingServer]
    [org.apache.zookeeper.data Stat]
    [org.apache.curator.framework CuratorFrameworkFactory CuratorFramework]
    [org.apache.curator.framework.state ConnectionStateListener ConnectionState]
    [org.apache.curator.framework.api CuratorWatcher PathAndBytesable Versionable GetDataBuilder
     SetDataBuilder DeleteBuilder ExistsBuilder GetChildrenBuilder Pathable Watchable]
    [org.apache.curator.framework.state ConnectionStateListener ConnectionState]
    [org.apache.curator.framework.imps CuratorFrameworkState]
    [org.apache.curator RetryPolicy]
    [org.apache.curator.retry RetryOneTime RetryNTimes BoundedExponentialBackoffRetry]))

(def reconnect-retries 24)

(defn ^CuratorFramework connect-1-retry
  ([connection-string]
   (connect-1-retry connection-string ""))
  ([connection-string ns]
   (connect-1-retry connection-string ns (RetryNTimes. 5 1000)))
  ([connection-string ns ^RetryPolicy retry-policy]
   (doto
       (.. (CuratorFrameworkFactory/builder)
           (namespace ns)
           (connectString       connection-string)
           (retryPolicy         retry-policy)
           (connectionTimeoutMs 10000) ; 10s
           (sessionTimeoutMs    15000) ; 15s
           (build))
     .start)))

(defn ^CuratorFramework connect-n-retry
  ([connection-string]
   (connect-n-retry connection-string ""))
  ([connection-string ns]
   (connect-n-retry connection-string ns (RetryOneTime. 5000)))
  ([connection-string ns ^RetryPolicy retry-policy]
   (doto
       (.. (CuratorFrameworkFactory/builder)
           (namespace ns)
           (connectString       connection-string)
           (retryPolicy         retry-policy)
           (connectionTimeoutMs 5000)  ; 5s
           (sessionTimeoutMs    10000) ; 10s
           (build))
     .start)))

(defn close
  "Closes the connection to the ZooKeeper server."
  [^CuratorFramework client]
  (.close client))

(defn try-connect [^CuratorFramework conn]
  (println "Trying connect ZK 5s ...")
  (.. conn
      (blockUntilConnected 5 TimeUnit/SECONDS)))

(defn block-until-connected [^CuratorFramework conn]
  (loop []
    (or (try-connect conn)
        (recur))))

; conn reconnect
(defn until-connected [^CuratorFramework conn restart-ch]
  (future (when-let [v (<!! restart-ch)]
            ; exclude null when channel closed
            (when v
                  (block-until-connected conn)))))

; try reconnect for some reasonable time or die
(defn connect-or-die [^CuratorFramework conn supervisor-ch max-retries ]
  (loop [retry max-retries]   ; 24 x 5s = 2 min
    (if (= 0 retry)
      (do #_(fatal "Couldn't connect with Zookeeper for a while")
          (println "Couldn't connect with Zookeeper for a while")
          (go (>! supervisor-ch :zk-no-connection))
          false)
      (or (try-connect conn)
          (recur (dec retry))))))

(defn try-reconnect-or-die [^CuratorFramework conn restart-ch supervisor-ch max-retries]
  (future (when-let [v (<!! restart-ch)]
            ; exclude null when channel closed
            (when v
              (connect-or-die conn supervisor-ch max-retries)))))

(defn as-connection-listener [f]
  (reify ConnectionStateListener
    (stateChanged [_ conn newState]
      (f newState))))

(defn add-conn-watcher [^CuratorFramework conn listener-fn]
  (let [listener (as-connection-listener listener-fn)]
    (.. conn
        getConnectionStateListenable
        (addListener listener))
    listener))

(defn remove-conn-watcher [^CuratorFramework conn listener]
  (.. conn
      getConnectionStateListenable
      (removeListener listener)))

; needs restart on conn lost
(defn notify-restarter [^CuratorFramework conn restart-ch]
  (add-conn-watcher conn
                    (fn [newState]
                      (println "ZK connection state:" (str newState))

                      ; try connect in bg when connection lost
                      (when (= ConnectionState/LOST newState)
                        (go (>! restart-ch true))))))
