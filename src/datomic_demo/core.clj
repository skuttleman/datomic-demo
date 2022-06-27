(ns datomic-demo.core
  (:require
   [clj-uuid :as uuid]
   [datomic-demo.data :as data]
   [datomic.client.api :as d]))

(def ^:private conn
  (data/connect "demo"))

(data/load-edn! conn "schema.edn")

(data/load-edn! conn "seed.edn")

(defn ^:private reduce-pairs [pairs]
  (reduce (fn [m [k v]]
            (update m k (fnil conj #{}) v))
          {}
          pairs))

(comment
  ;; *** QUERIES ***
  ;; all warriors' names
  (d/q '[:find ?name
         :where
         [_ :warrior/name ?name]]
       (d/db conn))


  ;; all warriors with any allergy
  (reduce-pairs (d/q '[:find ?name ?allergy
                       :where
                       [?e :warrior/name ?name]
                       [?e :warrior/allergies ?allergy]]
                     (d/db conn)))

  ;; all warriors with no allergies
  (d/q '[:find ?name
         :where
         [?e :warrior/name ?name]
         (not [?e :warrior/allergies])]
       (d/db conn))


  ;; all warriors _with_ PEANUTS allergy
  (d/q '[:find ?name
         :where
         [?e :warrior/name ?name]
         [?e :warrior/allergies :PEANUTS]]
       (d/db conn))


  ;; all warriors _without_ PEANUTS or SPINACH allergy
  (d/q '[:find ?name
         :where
         [?e :warrior/name ?name]
         (not [?e :warrior/allergies :PEANUTS])
         (not [?e :warrior/allergies :SPINACH])]
       (d/db conn))


  ;; all warriors with their allies
  (reduce-pairs (d/q '[:find ?name ?ally
                       :where
                       [?e :warrior/name ?name]
                       [?e :warrior/allies ?a]
                       [?a :warrior/name ?ally]
                       (not [?e :warrior/allergies :PEANUTS])
                       [?a :warrior/allergies :PEANUTS]]
                     (d/db conn)))


  ;; *** TRANSACTIONS ***
  ;; asserting
  (def joa-id (uuid/squuid))
  (d/transact conn {:tx-data [{:warrior/id joa-id
                               :warrior/name "Joan of Arc"
                               :warrior/weapon {:weapon/type :weapon-type/SWORD
                                                :weapon/damage 300}
                               :warrior/allergies #{:CAT}}
                              {:db/id "datomic.tx"
                               :db/doc "France's heroine"}]})
  (d/q '[:find (pull ?e [*])
         :in $ ?id
         :where
         [?e :warrior/id ?id]]
       (d/db conn)
       joa-id)
  (and (def tx (ffirst (d/q '[:find (pull ?tx [*])
                              :in $ ?id
                              :where
                              [_ :warrior/id ?id ?tx]]
                            (d/db conn)
                            joa-id)))
       tx)
  (d/transact conn {:tx-data [(-> tx
                                  (dissoc :db/txInstant) ;; :db/txInstant cannot be changed on existing transactions
                                  (assoc :warrior/name "YOLO"))]})


  ;; retracting
  ;; vector-style transaction data can either use the :db/id of an entity or a unique identifier defined in your schema
  (d/transact conn {:tx-data [[:db/retract (:db/id tx) :warrior/name "YOLO"]
                              [:db/add [:warrior/id joa-id] :warrior/allergies :FIRE]
                              [:db/add "datomic.tx" :db/doc "Whoopsie, missed some stuff"]]})


  (def raphael-id (ffirst (d/q '[:find ?id
                                 :where
                                 [?e :warrior/name "Raphael"]
                                 [?e :warrior/id ?id]]
                               (d/db conn))))


  ;; db is an immutable value
  (def before (d/db conn))
  (d/transact conn {:tx-data [[:db/retract [:warrior/id raphael-id] :warrior/allergies :PEANUTS]]})
  ;; before db still reflects Raphael's PEANUTS allergy
  (d/q '[:find ?name ?allergy
         :in $ ?id
         :where
         [?e :warrior/id ?id]
         [?e :warrior/name ?name]
         [?e :warrior/allergies ?allergy]]
       before
       raphael-id)
  ;; current db reflects the allergy being removed
  (d/q '[:find ?name ?allergy
         :in $ ?id
         :where
         [?e :warrior/id ?id]
         [?e :warrior/name ?name]
         [?e :warrior/allergies ?allergy]]
       (d/db conn)
       raphael-id)

  ;; isComponent
  (d/q '[:find (pull ?w [*])
         :where
         [?w :weapon/type :weapon-type/SWORD]]
       (d/db conn))
  (reduce-pairs (d/q '[:find ?name ?ally
                       :where
                       [?e :warrior/name ?name]
                       [?e :warrior/allies ?a]
                       [?a :warrior/name ?ally]]
                     (d/db conn)))
  ;; the weapon is a component, and will also be retracted
  (d/transact conn {:tx-data [[:db/retractEntity (ffirst (d/q '[:find ?e
                                                                :where
                                                                [?e :warrior/name "Leonardo"]]
                                                              (d/db conn)))]]}))

(comment
  ;; history
  (def chuck-id (uuid/squuid))

  (do (d/transact conn {:tx-data [[:db/add "contact" :emergency-contact/id chuck-id]
                                  [:db/add "contact" :emergency-contact/name "Charles VII"]
                                  [:db/add [:warrior/id joa-id] :warrior/emergency-contacts "contact"]]})
      (d/transact conn {:tx-data [[:db/add [:emergency-contact/id chuck-id] :emergency-contact/email "chucky7@juno.com"]
                                  [:db/add [:warrior/id joa-id] :warrior/name "Joanie"]]})
      (d/transact conn {:tx-data [[:db/add [:emergency-contact/id chuck-id] :emergency-contact/email "charlie.seven@gmail.com"]]})
      (d/transact conn {:tx-data [[:db/retract [:emergency-contact/id chuck-id] :emergency-contact/email "charlie.seven@gmail.com"]
                                  [:db/retract [:warrior/id joa-id] :warrior/name "Joanne"]]})
      (d/transact conn {:tx-data [[:db/add [:emergency-contact/id chuck-id] :emergency-contact/name "Charles the Seventh"]
                                  [:db/add [:warrior/id raphael-id] :warrior/name "Raph"]]})
      (d/transact conn {:tx-data [[:db/add [:emergency-contact/id chuck-id] :emergency-contact/email "charlie.seven@gmail.com"]
                                  [:db/add [:warrior/id joa-id] :warrior/name "Joan of Arc"]]}))

  (->> (d/q '[:find ?tx ?name ?op ?dt
              :in $ ?id
              :where
              [?e :emergency-contact/id ?id]
              [?e :emergency-contact/name ?name ?tx ?op]
              [?tx :db/txInstant ?dt]]
            (d/history (d/db conn))
            chuck-id)
       (group-by first)
       (sort-by first)
       (map second))
  (->> (d/q '[:find ?tx ?email ?op ?dt
              :in $ ?id
              :where
              [?e :emergency-contact/id ?id ?name-tx]
              (or-join [?e ?email ?tx ?op]
                       [?e :emergency-contact/email ?email ?tx ?op]
                       (and [(ground "no email") ?email]
                            [(ground false) ?op]
                            [?e :emergency-contact/id _ ?tx]))
              [?tx :db/txInstant ?dt]]
            (d/history (d/db conn))
            chuck-id)
       (group-by first)
       (sort-by first)
       (map second))


  ;; as of / since
  (def dt (java.util.Date.))
  (d/q '[:find ?name ?email
         :in $ ?id
         :where
         [?e :emergency-contact/id ?id]
         [?e :emergency-contact/name ?name]
         [?e :emergency-contact/email ?email]]
       (d/as-of (d/db conn) dt)
       chuck-id)
  ;; using since in the same way produces no results because :warrior/id was added to db _BEFORE_ timestamp
  (d/q '[:find ?name ?email
         :in $ ?id
         :where
         [?e :emergency-contact/id ?id]
         [?e :emergency-contact/name ?name]
         [?e :emergency-contact/email ?email]]
       (d/since (d/db conn) dt)
       chuck-id)
  ;; pass multiple db values
  (d/q '[:find ?name ?email
         :in $all $since ?id
         :where
         [$all ?e :emergency-contact/id ?id]
         [$since ?e :emergency-contact/name ?name]
         [$since ?e :emergency-contact/email ?email]]
       (d/db conn)
       (d/since (d/db conn) dt)
       chuck-id)

  ;; query warrior names that have changed
  (->> (d/q '[:find ?old-name ?new-name
              :in $all $old $new
              :where
              [$all ?e :warrior/id]
              [(get-else $old ?e :warrior/name :MISSING) ?old-name]
              [(get-else $new ?e :warrior/name :MISSING) ?new-name]
              [(not= ?old-name ?new-name)]]
            (d/db conn)
            (d/as-of (d/db conn) dt)
            (d/since (d/db conn) dt))
       (remove (comp #(contains? % :MISSING) set))))
