(ns clj-pdf.section
  (:require [clj-pdf.utils :refer [split-classes-from-tag get-class-attributes]])
  (:import com.lowagie.text.Chunk))


(declare ^:dynamic *cache*)


(defmulti render (fn [tag meta & els] tag))
(defmethod render :default [tag meta & els]
  (throw (ex-info (str "invalid tag: " tag) {:meta meta :content els})))


(defn- keywordize [value]
  (if (string? value) (keyword value) value))


(defn make-section
  ([element]
   (cond
     (instance? Chunk element) element
     (empty? element) (Chunk. "")
     (every? sequential? element) (doseq [item element]
                                    (make-section item))
     element (make-section {} element)
     :else (Chunk. "")))
  ([meta element]
   (try
     (cond
       (instance? Chunk element) element
       (string? element) (Chunk. element)
       (nil? element) (Chunk. "")
       (number? element) (Chunk. (str element))
       :else
       (let [[tag & content] element
             tag        (keywordize tag)
             [tag & classes] (split-classes-from-tag tag)
             [attrs elements] (if (map? (first content))
                                [(first content) (rest content)]
                                [nil content])
             stylesheet (:stylesheet meta)
             new-meta   (cond-> meta
                                stylesheet (merge (get-class-attributes stylesheet classes))
                                attrs (merge attrs))]

         (apply render tag new-meta elements)))
     (catch Exception e
       (prn meta element)
       (throw (ex-info "failed to parse element" {:meta meta :element element} e))))))


(defn make-section-or [if-string meta item]
  (if (string? item)
    (render if-string meta item)
    (make-section meta item)))


;; that require is here to overcome circular import
(require '[clj-pdf.section core cell chart svg table])
