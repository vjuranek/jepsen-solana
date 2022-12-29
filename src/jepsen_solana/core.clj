(ns solana.core
    (:require [clojure.java.io :as io]
              [clojure.tools.logging :refer :all]
              [clojure.string :as str]
              [jepsen
               [cli :as cli]
               [control :as c]
               [db :as db]
               [tests :as tests]]
              [jepsen.control.util :as cu]
              [jepsen.os.debian :as debian]))

(def solana-dir "/opt/solana")

(defn validator
  "Solana validator"
  [version]
  (reify
   db/DB
   (setup! [_ test node]
           (info node "installing Solana validator" version)
           (c/su
            (let [url (str "https://github.com/solana-labs/solana/releases/download/" version
                           "/solana-release-x86_64-unknown-linux-gnu.tar.bz2")]
              (cu/install-archive! url solana-dir)))
           ;;(start!)
           (info node "Solana installed in " solana-dir))

   (teardown! [_ test node]
              (info node "tearing down Solana validator")
              ;;(stop!)
              )))

(defn solana-test
  "Given an options map from the command line runner (e.g. :nodes, :ssh,
  :concurrency, ...), constructs a test map."
  [opts]
  (merge tests/noop-test
         opts
         {:name            "solana"
          :os              debian/os
          :db              (validator "v1.14.10")
          :pure-generators true
          :nodes           ["n1"]}))

(defn -main
  "Handles command line arguments. Can either run a test, or a web server for
  browsing results."
  [& args]
  (cli/run! (cli/single-test-cmd {:test-fn solana-test})
            args))
