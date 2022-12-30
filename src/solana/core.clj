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
              [jepsen.os.debian :as debian]
              [slingshot.slingshot :refer [try+ throw+]]))

(def solana-dir "/opt/solana")
(def solana-unit-file "/etc/systemd/system/solana.service")

(defn download-release!
  [node, version]
  (info node "Installing Solana validator" version)
  (c/su
   (let [url (str "https://github.com/solana-labs/solana/releases/download/" version
                  "/solana-release-x86_64-unknown-linux-gnu.tar.bz2")]
     (cu/install-archive! url solana-dir))))

(defn create-unit-file!
  []
  (info "Creating systemd unit file for Solana validator")
  (c/su
   (c/exec :echo
              (-> "solana.service"
                  io/resource
                  slurp)
           :> solana-unit-file)))

(defn configure!
  []
  (create-unit-file!)
  (c/su
   (c/exec :systemctl :daemon-reload)))

(defn start!
  "Starts Solana validator."
  []
  (c/su
   (c/exec :systemctl :start :solana)))

(defn stop!
  "Stops Solana validator."
  []
  (try+
   (c/su
    (c/exec :systemctl :stop :solana))
   (catch [:exit 5] e
     "Service not loaded")))

(defn status
  "Returns the status of the Solana validator."
  []
  (try+
   (c/su
    (c/exec :systemctl :status :solana))
   (catch [:exit 3] e
     "Service not running")
   (catch [:exit 4] e
     "Service not defined")))

(defn validator
  "Solana validator"
  [version]
  (reify
   db/DB
   (setup! [_ test node]
           (download-release! node version)
           (configure!)
           (start!)
           (info node "Solana status: " (status)))

   (teardown! [_ test node]
              (info node "tearing down Solana validator")
              (stop!)
              (info node "Solana status: " (status)))))

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
