(ns datomic-tester.core
  (:require [datomic.api :as d])
  (:import  [datomic Peer Util] ;; Imports only used in data loading
            [java.io FileReader]))

(comment 

  ;; A Tour of Datomic - a time travelling database

  (def persistent-storage "datomic:dev://localhost:4334/my-db")

  ;; In-memory, gone after disconnect I imagine...
  (def conn-uri "datomic:mem://library")
  
  ;; Create database from URI
  (d/create-database conn-uri)

  ;; Connect to existing database via URI
  (def conn (d/connect conn-uri))

  (def empty-db (d/db conn))

  ;; Load Sample data
  (do
    ;; Setup sample schema
    (d/transact conn
              (first (Util/readAll (FileReader. "library-schema.edn"))))

    ;; load sample data
    (d/transact conn
              (first (Util/readAll (FileReader. "library-data0.edn"))))

    )
  
  ;; find all institutions (i.e. entities that have a :uni/name)
  (d/q '[:find ?e
       :where
       [?e :uni/name]]
     (d/db conn))

  (d/q '[:find ?e :where [?e :uni/name]] empty-db)

  ;; find all records,
  ;; then get entities for each,
  ;; then extract the record name
  (let [db (d/db conn)]
    (map (comp :uni/name
              #(d/entity db %)
              first)
        (d/q '[:find ?e :where [?e :uni/name]] db)))

  ;; Find their names
  (d/q '[:find ?n
       :where
       [?u :uni/name ?n] ]
     (d/db conn))
  
  ;; Define query parameters
  (d/q '[:find ?e ?n
       :where
       [?e :uni/country :country/uk]
       [?e :uni/name ?n]]
     (d/db conn))

  ;; Use full-text search
  (d/q '[:find ?e ?n
       :where
       [(fulltext $ :project/name "history") [[?e ?n]]]]
     (d/db conn))
  




  ;; Query by chained relationships
  (d/q '[:find ?p_name ?u_name
       :where
       [?p :project/name ?p_name]
       [?p :project/owner ?u]
       [?u :user/uni ?uni]
       [?uni :uni/name ?u_name]]
     (d/db conn))






  ;; Query by chained relationships
  (d/q '[:find ?p_name ?u_name
       :where

       [?p :project/name ?p_name]

       [?p :project/owner ?u]
                         [?u :user/uni ?uni]
                                      [?uni :uni/name ?u_name]]
     (d/db conn))

  ;; Retrieve an individual entity
  (-> (d/db conn)
      (d/entity 17592186045431)
      first)

  ;; Using rules, think of them as awesome SQL WHERE macros
  (def twitter-rule '[[[twitter ?c] [?c :community/type :community.type/twitter]]])
  (q '[:find ?n
       :in $ %
       :where
       [?c :community/name ?n]
       (twitter ?c)]
     (db conn)
     twitter-rule)

  ;; More complex rules
  (def community-to-region '[[[region ?c ?r]
                              [?c :community/neighborhood ?n]
                              [?n :neighborhood/district ?d]
                              [?d :district/region ?re]
                              [?re :db/ident ?r]]])  
  (q '[:find ?n
       :in $ %
       :where
       [?c :community/name ?n]
       (region ?c :region/ne)]
     (db conn)
     community-to-region)

  ;; Example of an OR in a rule
  (def or-rule-example '[[[social-media ?c]
                          [?c :community/type :community.type/twitter]]
                         [[social-media ?c]
                          [?c :community/type :community.type/facebook-page]]])

  ;; Rules can define rules in a hierachy
  (def hierarchical-rules '[[[region ?c ?r]
                             [?c :community/neighborhood ?n]
                             [?n :neighborhood/district ?d]
                             [?d :district/region ?re]
                             [?re :db/ident ?r]]
                            [[social-media ?c]
                             [?c :community/type :community.type/twitter]]
                            [[social-media ?c]
                             [?c :community/type :community.type/facebook-page]]
                            [[northern ?c] (region ?c :region/ne)]
                            [[northern ?c] (region ?c :region/n)]
                            [[northern ?c] (region ?c :region/nw)]
                            [[southern ?c] (region ?c :region/sw)]
                            [[southern ?c] (region ?c :region/s)]
                            [[southern ?c] (region ?c :region/se)]])
  (q '[:find ?n
       :in $ %
       :where
       [?c :community/name ?n]
       (northern ?c)
       (social-media ?c)]
     (db conn)
     hierarchical-rules)  
  
  ;; Modifying the data queried against, but *WITHOUT* changing the DB
  (count (q '[:find ?c :where [?c :community/name]]
            (d/with (db conn) future-data)))


  ;; Transactions
  ;; ------------
  ;; Anything in a map counts as ADD/UPDATE (depending on if the id already exists or not)
  (d/transact conn
              '[{:db/id #db/id [:db.part/user]
                 :uni/name "Loughborough"}])

  ;; Add more than one record at a time
  (d/transact conn
              '[{:db/id #db/id [:db.part/user]
                 :user/name "foo"}
                {:db/id #db/id [:db.part/user]
                 :user/name "bar"}
                {:db/id #db/id [:db.part/user]
                 :user/name "baz"}])

  ;; Add new properties
  (d/transact conn
              '[{:db/id #db/id[:db.part/db]
                  :db/ident :citeproc/title
                  :db/valueType :db.type/string
                  :db/cardinality :db.cardinality/one
                  :db/doc "CSL title"
                  :db.install/_attribute :db.part/db}
                {:db/id #db/id[:db.part/db]
                  :db/ident :citeproc/author
                  :db/doc "CSL author"
                  :db/valueType :db.type/string
                  :db/cardinality :db.cardinality/many
                  :db.install/_attribute :db.part/db}
                ])
  (let [proj-id (-> (d/q '[:find ?c
                        :where
                        [?c :project/name "Chemistry 101"]] (d/db conn))
                   first
                   first)]
    (d/transact conn
                `[{:db/id #db/id[:db.part/user]
                   :reference/project ~proj-id
                   :citeproc/title "À la recherche du temps perdu"
                   :citeproc/author "Marcel Proust"
                   }
                  {:db/id #db/id[:db.part/user]
                   :reference/project ~proj-id
                   :citeproc/title "Les plaisirs et les jour"
                   :citeproc/author "Marcel Proust"
                   }
                  ]))

  (defn projects-with-works-by-proust [db]
    (d/q '[:find [?p ?p_title]
          :where
          [?p :project/name ?p_title]
          [?r :reference/project ?p]
          [?r :citeproc/author "Marcel Proust"]]
         db))

  (def prior-state (d/db conn))

  ;; Updates and deletions

  ;; Transaction, update records
  (let [proj-id (-> (d/q '[:find ?c
                        :where
                        [?c :project/name "Chemistry 101"]] (d/db conn))
                   first
                   first)]
    (d/transact conn ;; Note use back-tick, so we can unquote the value of baz-id
                `[{:db/id ~proj-id :project/name "French Literature"}]))

  (defn n-users [db]
    (->> db
         (d/q '[:find ?u :where [?u :user/name]])
         count
         (str "Users: ")))

  (n-users (d/db conn))

  ;; Transaction, delete record
  (let [delendum (-> (d/q '[:find ?c
                        :where
                        [?c :user/name "foo"]]
                      (d/db conn))
                   first
                   first)] ;; get the ID to modify
    (d/transact conn
                `[[:db/retract ~delendum :user/name "foo"]]))

  (n-users (d/db conn))
  (n-users prior-state)

  ;; Finding changes

  (d/q '[:find ?when
      :where
      [?tx :db/txInstant ?when]]
    (d/db conn))

  (let [changes-since-snapshot (d/since (d/db conn)
                                        (d/basis-t prior-state))]
    (d/q '[:find ?when
          :where
          [?tx :db/txInstant ?when]]
         changes-since-snapshot))

  ;; Analysing history:

  ;; Query and define the various DB states
  (def db-dates (->> (d/q '[:find ?when
                          :where
                          [?tx :db/txInstant ?when]]
                        (d/db conn))
                     seq
                     (map first)
                     sort))

  ;; Query against the past db state
  (let [db (d/db conn)
        query '[:find ?p_name :where [?p :project/name ?p_name]]]
    (for [date db-dates]
      [date
       (->> (d/as-of db date)
            (d/q query)
            (apply concat)
            vec)]))

  (let [db (d/db conn)
        query '[:find ?u :where [?u :user/name]]]
    (for [date db-dates]
      [date
       (->> (d/as-of db date)
            (d/q query)
            (apply concat)
            count)]))
  
  ) ;; END COMMENT SECTION
