(defproject rosado.aleph.experiments "1.0.0-SNAPSHOT"
  :description "Experimenting with Aleph, a netty based framework."
  :dependencies [[org.clojure/clojure "1.2.0-beta1"]
                 [org.clojure/clojure-contrib "1.2.0-beta1"]
                 [aleph "0.1.0-SNAPSHOT"]
                 [ring/ring-core "0.2.5"]
                 [hiccup "0.2.6"]
                 [log4j "1.2.15" :exclusions [javax.mail/mail
                                              javax.jms/jms
                                              com.sun.jdmk/jmxtools
                                              com.sun.jmx/jmxri]]]
  :dev-dependencies [[swank-clojure "1.2.1"]])
