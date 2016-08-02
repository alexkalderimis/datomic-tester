(defproject datomic-tester "1.0.0-SNAPSHOT"
  :description "For playing with Datomic, and for reference"
  :dependencies [[org.clojure/clojure "1.8.0"]

                 ;; NOTE, you must do the following command from your datomic installation for this to work,
                 ;; $ mvn install:install-file -DgroupId=com.datomic -DartifactId=datomic -Dfile=datomic-${DATOMIC_VERSION}.jar 
                 [com.datomic/datomic-pro "0.9.5385"]
                 ] 
  )
