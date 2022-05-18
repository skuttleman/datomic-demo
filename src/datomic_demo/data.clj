(ns datomic-demo.data
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [datomic.client.api :as d]))

(def ^:const cfg
  {:server-type :peer-server
   :access-key "myaccesskey"
   :secret "mysecret"
   :endpoint "localhost:8998"
   :validate-hostnames false})

(def ^:private client
  (d/client cfg))

(def ^{:arglists '([db-name])} connect
  (memoize (fn [db-name]
             (d/connect client {:db-name db-name}))))

(defn read-resource [resource-name]
  (-> resource-name io/resource slurp edn/read-string))

(defn load-edn! [conn resource-name]
  (d/transact conn {:tx-data (read-resource resource-name)}))
