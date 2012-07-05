(ns tower.utils
  {:author "Peter Taoussanis"}
  (:require [clojure.string :as str]
            [clojure.java.io :as io])
  (:import  [java.io File]))

(defn leaf-paths
  "Takes a nested map and squashes it into a sequence of paths to leaf nodes.
  Based on 'flatten-tree' by James Reaves on Google Groups.

  (leaf-map {:a {:b {:c \"c-data\" :d \"d-data\"}}}) =>
  ((:a :b :c \"c-data\") (:a :b :d \"d-data\"))"
  [map]
  (if (map? map)
    (for [[k v] map
          w (leaf-paths v)]
      (cons k w))
    (list (list map))))

(defn escape-html
  "Changes some common special characters into HTML character entities."
  [s]
  (-> (str s)
      (str/replace "&"  "&amp;")
      (str/replace "<"  "&lt;")
      (str/replace ">"  "&gt;")
      (str/replace "\"" "&quot;")))

(comment (escape-html "\"Word\" & <tag>"))

(defn inline-markdown->html
  "Uses regex to parse given markdown string into HTML. Supports only strong,
  emph, and a context-spexific alternative style tag. Doesn't do any escaping."
  [markdown]

  (-> markdown
      (str/replace #"\*\*(.+?)\*\*" "<strong>$1</strong>")
      (str/replace #"__(.+?)__"     "<strong>$1</strong>")
      (str/replace #"\*(.+?)\*"     "<emph>$1</emph>")
      (str/replace #"_(.+?)_"       "<emph>$1</emph>")

      ;; "alt" span (define in surrounding CSS scope)
      (str/replace #"~~(.+?)~~" "<span class=\"alt\">$1</span>")))

(comment (inline-markdown->html "**strong** *emph* ~~alt~~ <tag>"))

(defmacro defmem-
  "Defines a type-hinted, private memoized fn."
  [name type-hint fn-params fn-body]
  ;; To allow type-hinting, we'll actually wrap a closed-over memoized fn
  `(let [memfn# (memoize (~'fn ~fn-params ~fn-body))]
     (defn ~(with-meta (symbol name) {:private true})
       ~(with-meta '[& args] {:tag type-hint})
       (apply memfn# ~'args))))

(defn ttl-memoize
  "Like `memoize` but invalidates the cache for a set of arguments after TTL
  msecs has elapsed."
  [ttl f]
  (let [cache (atom {})]
    (fn [& args]
      (let [{:keys [time-cached d-result]} (get @cache args)
            now (System/currentTimeMillis)]

        (if (and time-cached (< (- now time-cached) ttl))
          @d-result
          (let [d-result (delay (apply f args))]
            (swap! cache assoc args {:time-cached now :d-result d-result})
            @d-result))))))

(def some-file-resources-modified?
  "Returns true iff any of the files backing given resources have changed
  since this function was last called. Ignores invalid files."
  (let [times (atom {})]
    (fn modified?
      ([resource-name & more] (some modified? (cons resource-name more)))
      ([resource-name]
         (when-let [^File file (try (->> resource-name io/resource io/file)
                                    (catch Exception _ nil))]
           (let [last-modified (.lastModified file)]
             (let [file-name (str file)
                   modified? (> last-modified (@times file-name 0))]
               (when modified? (swap! times assoc file-name last-modified))
               modified?)))))))