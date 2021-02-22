(ns datalevin.main
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.string :as s]
            [clojure.pprint :as p]
            [clojure.stacktrace :as st]
            [sci.core :as sci]
            [datalevin.core :as d]
            [datalevin.util :refer [raise]]
            [datalevin.bits :as b]
            [datalevin.lmdb :as l]
            [datalevin.binding.graal])
  (:gen-class))

(def version "0.4.0")

(def version-str
  (str
    "
  Datalevin (version: " version ")"))

(def commands #{"exec" "copy" "drop" "dump" "load" "stat" "help"})

(def stat-help
  "
  Command stat - show statistics of the main database or a sub-database.

  Required option:
      -d --dir PATH   Path to the database directory
  Optional arguments:
      name(s) of sub-database(s)

  Examples:
      dtlv -d /data/companydb stat
      dtlv -d /data/companydb stat sales products")

(def dump-help
  "
  Command dump - dump the content of the database or a sub-database.

  Required option:
      -d --dir PATH  Path to the database directory
  Optional options:
      -a --all        All of the sub-databases
      -f --file PATH  Write to the specified file instead of the standard output
      -l --list       List the names of sub-databases instead of the content
  Optional arguments:
      name(s) of sub-database(s)

  Examples:
      dtlv -d /data/companydb -a dump
      dtlv -d /data/companydb -l dump
      dtlv -d /data/companydb -f ~/sales-data dump sales")

(def load-help
  "
  Command load - load data into the database or a sub-database.

  Required option:
      -d --dir  PATH  Path to the database directory
  Optional option:
      -f --file PATH  Load from the specified file instead of the standard input
      -t --text       Input is a simple text format: paired lines text, where
                      the first line is the key, the second the value
  Optional argument:
      Name of the sub-database to load the data into

  Examples:
      dtlv -d /data/companydb -f ~/sales-data load sales")

(def copy-help
  "
  Command copy - Copy the database. This can be done regardless of whether it is
  currently in use.

  Required option:
      -d --dir PATH   Path to the database directory
  Optional option:
      -c --compact    Compact while copying. Only pages in use will be copied.
  Optional argument:
      Path of the destination directory if specified, otherwise, the copy is
      written to the standard output.

  Examples:
      dtlv -d /data/companydb -c copy /backup/companydb-2021-02-14")

(def drop-help
  "
  Command drop - Drop or clear the content of sub-database(s).

  Required option:
      -d --dir PATH   Path to the database directory
  Optional option:
      -D --delete     Delete the sub-database, not just empty it.
  Optional argument:
      Name(s) of the sub-database(s), otherwise, the main database is operated on

  Examples:
      dtlv -d /data/companydb -D drop sales")

(def exec-help
  "
  Command exec - Execute database transactions or queries.

  Required argument:
      The code to be executed.

  Examples:
      dtlv exec (def conn (get-conn '/data/companydb')) \\
                (transact! conn [{:name \"Datalevin\"}])")

(def repl-header
  "
  Type (help) to see available functions. Clojure core functions are also available.
  Type (exit) to exit.
  ")

(defn- usage [options-summary]
  (->> [version-str
        ""
        "Usage: dtlv [options] [command] [arguments]"
        ""
        "Commands:"
        "  exec  Execute database transactions or queries"
        "  copy  Copy a database, regardless of whether it is now in use"
        "  drop  Drop or clear a database"
        "  dump  Dump the content of a database to standard output"
        "  load  Load data from standard input into a database"
        "  stat  Display statistics of database"
        ""
        "Options:"
        options-summary
        ""
        "Omit the command to enter the interactive shell."
        "See 'dtlv help <command>' to read about a specific command."]
       (s/join \newline)))

(defn- error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (s/join \newline errors)))

(def cli-opts
  [["-a" "--all" "Include all of the sub-databases"]
   ["-c" "--compact" "Compact while copying."]
   ["-d" "--dir PATH" "Path to the database directory"]
   ["-D" "--delete" "Delete the sub-database, not just empty it"]
   ["-f" "--file PATH" "Path to the specified file"]
   ["-h" "--help" "Show usage"]
   ["-l" "--list" "List the names of sub-databases instead of the content"]
   ["-t" "--text" "Load data from a simple text format: paired lines of text"]
   ["-V" "--version" "Show Datalevin version and exit"]])

(defn- validate-args
  "Validate command line arguments. Either return a map indicating the program
  should exit (with a error message, and optional ok status), or a map
  indicating the action the program should take and the options provided."
  [args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-opts)
        command                                    (first arguments)]
    (cond
      (:version options) {:exit-message version-str :ok? true}
      (:help options)    {:exit-message (usage summary) :ok? true}
      errors             {:exit-message (str (error-msg errors)
                                             \newline
                                             (usage summary))}
      (commands command) {:command   command
                          :options   options
                          :arguments (rest arguments)
                          :summary   summary}
      (nil? command)     {:command "repl" :options options}
      :else              {:exit-message (usage summary)})))

(defn- exit
  ([]
   (exit 0))
  ([status]
   (System/exit status))
  ([status msg]
   (println msg)
   (System/exit status)))

(defn- dtlv-help [arguments summary]
  (if (seq arguments)
    (let [command (s/lower-case (first arguments))]
      (exit 0 (case command
                "exec" exec-help
                "copy" copy-help
                "drop" drop-help
                "dump" dump-help
                "load" load-help
                "stat" stat-help
                (str "Unknown command: " command))))
    (exit 0 (usage summary))))

(defn- dtlv-exec [options arguments]
  #_(try

      (catch Exception e
        (println (str "Execution error: " (.getMessage e)))
        (st/print-cause-trace e)))
  (exit 0))

(defn- dtlv-copy [options arguments]
  )

(defn- dtlv-dump [options arguments]
  )

(defn- dtlv-load [options arguments]
  )

(defn- dtlv-stat [{:keys [dir]} arguments]
  (assert dir (str "Missing data directory path.\n" stat-help))
  (let [dbi  (first arguments)
        lmdb (l/open-lmdb dir)]
    (p/pprint (if dbi
                (do (l/open-dbi lmdb dbi)
                    (l/stat lmdb dbi))
                (l/stat lmdb)))
    (l/close-lmdb lmdb))
  (exit 0))

(defn- dtlv-drop [options arguments]
  )

(defn- prompt [ctx]
  (let [ns-name (sci/eval-string* ctx "(ns-name *ns*)")]
    (print (str ns-name "> "))
    (flush)))

(defn- handle-error [_ctx last-error e]
  (binding [*out* *err*] (println (ex-message e)))
  (sci/set! last-error e))

(def user-facing-ns #{'datalevin.core 'datalevin.lmdb})

(defn- user-facing? [v]
  (let [m (meta v)]
    (and (:doc m)
         (if-let [p (:protocol m)]
           (and (not (:no-doc (meta p)))
                (not (:no-doc m)))
           (not (:no-doc m))))))
(user-facing? datalevin.lmdb/get-txn)

(defn- user-facing-map [var-map]
  (select-keys var-map
               (keep (fn [[k v]] (when (user-facing? v) k)) var-map)))

(defn- user-facing-vars []
  (reduce
    (fn [m ns]
      (assoc m ns (user-facing-map (ns-publics ns))))
    {}
    user-facing-ns))

(defn- doc [s]
  (when-let [f (some #(ns-resolve % s) user-facing-ns)]
    (println (:doc (meta f)))))

(defn- repl-help []
  (println "")
  (println "Call function just like in code, i.e. (<function> <args>)")
  (println "")
  (println "The following Datalevin functions are available:")
  (println "")
  (doseq [ns user-facing-ns]
    (print (str "* Functions in " ns ": "))
    (doseq [f (sort-by name (keys (user-facing-map (ns-publics ns))))]
      (print (name f))
      (print " "))
    (println "")
    (println ""))
  (println "Type (doc <function>) to read documentation of the function"))

(defn- dtlv-repl [options]
  (println version-str)
  (println repl-header)
  (let [reader     (sci/reader *in*)
        last-error (sci/new-dynamic-var '*e nil
                                        {:ns (sci/create-ns 'clojure.core)})
        ctx        (sci/init {:namespaces
                              (merge (user-facing-vars)
                                     {'clojure.core {'*e last-error}})})]
    (sci/with-bindings {sci/ns     @sci/ns
                        last-error @last-error}
      (loop []
        (prompt ctx)
        (let [next-form (try (sci/parse-next ctx reader)
                             (catch Throwable e
                               (handle-error ctx last-error e)
                               ::err))]
          (cond
            (= next-form '(exit)) (exit)
            (= next-form '(help)) (do (repl-help) (recur))
            (= ((comp name first) next-form) "doc")
            (do (doc (first (next next-form))) (recur))
            :else
            (when-not (= ::sci/eof next-form)
              (when-not (= ::err next-form)
                (let [res (try (sci/eval-form ctx next-form)
                               (catch Throwable e
                                 (handle-error ctx last-error e)
                                 ::err))]
                  (when-not (= ::err res)
                    (prn res))))
              (recur))))))))

(defn -main [& args]
  (let [{:keys [command options arguments summary exit-message ok?]}
        (validate-args args)]
    (if exit-message
      (exit (if ok? 0 1) exit-message)
      (case command
        "repl" (dtlv-repl options)
        "exec" (dtlv-exec options arguments)
        "copy" (dtlv-copy options arguments)
        "drop" (dtlv-drop options arguments)
        "dump" (dtlv-dump options arguments)
        "load" (dtlv-load options arguments)
        "stat" (dtlv-stat options arguments)
        "help" (dtlv-help arguments summary)))))