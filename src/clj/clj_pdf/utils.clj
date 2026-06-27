(ns clj-pdf.utils
  (:require [clojure.string :refer [split]])
  (:import [java.awt Color]
           [java.net URL MalformedURLException]
           [com.lowagie.text.alignment HorizontalAlignment VerticalAlignment]
           [com.lowagie.text Element Font FontFactory]
           [com.lowagie.text.pdf BaseFont FontSelector]))


(def ^:dynamic *allowed-image-url-protocols*
  "URL protocols (lower-case) permitted when an :image source string parses as
   a URL. Defaults to http/https only; file:, jar:, ftp:, gopher: etc. are
   rejected to prevent SSRF and local-file disclosure (a user-influenced image
   source resolving to a file:// read or an internal/metadata host). Rebind to
   widen, e.g. (binding [*allowed-image-url-protocols* #{\"http\" \"https\" \"file\"}] ...)."
  #{"http" "https"})


(def ^:dynamic *allowed-image-url-host?*
  "Optional predicate (fn [host] -> truthy) applied to the host of a remote
   :image URL. nil (the default) imposes no host restriction. Set to an
   allowlist predicate to limit which hosts remote image fetches may reach,
   e.g. (binding [*allowed-image-url-host?* #{\"images.example.com\"}] ...)."
  nil)


(defn validate-image-url-string
  "Guard for string :image sources. OpenPDF's Image/getInstance resolves a
   string by trying (new URL s) first and only falling back to a local file on
   MalformedURLException. So a string that parses as a URL is policy-checked
   here; one that does not (a plain/relative/Windows file path) passes through
   unchanged, preserving the documented \"filename string\" feature.

   Throws ex-info with :type :clj-pdf.security/disallowed-image-url for a
   disallowed protocol, or :clj-pdf.security/disallowed-image-host for a host
   rejected by *allowed-image-url-host?*. Returns the string when allowed."
  ^String [^String s]
  (when-let [^URL url (try (URL. s) (catch MalformedURLException _ nil))]
    (let [protocol (some-> (.getProtocol url) (.toLowerCase))
          host     (.getHost url)]
      (when-not (contains? *allowed-image-url-protocols* protocol)
        (throw (ex-info (str "Image URL protocol \"" protocol "\" is not allowed. "
                             "Allowed protocols: " *allowed-image-url-protocols* ". "
                             "Source: " s)
                        {:type     :clj-pdf.security/disallowed-image-url
                         :url      s
                         :protocol protocol})))
      (when (and *allowed-image-url-host?*
                 (not (*allowed-image-url-host?* host)))
        (throw (ex-info (str "Image URL host \"" host "\" is not allowed. Source: " s)
                        {:type :clj-pdf.security/disallowed-image-host
                         :url  s
                         :host host})))))
  s)


(defn split-classes-from-tag
  [tag]
  (map keyword (split (name tag) #"\.")))


(defn get-class-attributes
  [stylesheet classes]
  (apply merge (map stylesheet classes)))


(defn get-color [color]
  (let [[r g b] color]
    (when (and r g b)
      (Color. (int r) (int g) (int b)))))

(defn get-horizontal-alignment [align]
  (case (when align (name align))
    "left"          HorizontalAlignment/LEFT
    "center"        HorizontalAlignment/CENTER
    "right"         HorizontalAlignment/RIGHT
    "justified"     HorizontalAlignment/JUSTIFIED
    "justified-all" HorizontalAlignment/JUSTIFIED_ALL
    "undefined"     HorizontalAlignment/UNDEFINED
    HorizontalAlignment/LEFT))

(defn get-vertical-alignment [align]
  (case (when align (name align))
    "baseline"  VerticalAlignment/BASELINE
    "bottom"    VerticalAlignment/BOTTOM
    "center"    VerticalAlignment/CENTER
    "top"       VerticalAlignment/TOP
    "undefined" VerticalAlignment/UNDEFINED
    VerticalAlignment))

(defn get-alignment [align]
  (case (when align (name align))
    "left"      Element/ALIGN_LEFT
    "center"    Element/ALIGN_CENTER
    "right"     Element/ALIGN_RIGHT
    "justified" Element/ALIGN_JUSTIFIED
    "top"       Element/ALIGN_TOP
    "middle"    Element/ALIGN_MIDDLE
    "bottom"    Element/ALIGN_BOTTOM
    Element/ALIGN_LEFT))


(defn get-style [style]
  (case (when style (name style))
    "bold"        Font/BOLD
    "italic"      Font/ITALIC
    "bold-italic" Font/BOLDITALIC
    "normal"      Font/NORMAL
    "strikethru"  Font/STRIKETHRU
    "underline"   Font/UNDERLINE
    Font/NORMAL))


(defn- compute-font-style [styles]
  (if (> (count styles) 1)
    (apply bit-or (map get-style styles))
    (get-style (first styles))))


(defn font ^Font
  [{:keys [style
           styles
           size
           color
           family
           ttf-name
           encoding
           subset?]}]

  (let [ttf      (or ttf-name
                     (case (when family (name family))
                       "courier"      FontFactory/COURIER
                       "helvetica"    FontFactory/HELVETICA
                       "times-roman"  FontFactory/TIMES_ROMAN
                       "symbol"       FontFactory/SYMBOL
                       "zapfdingbats" FontFactory/ZAPFDINGBATS
                       FontFactory/HELVETICA))

        encoding (case [(not (nil? ttf-name))
                        (if (keyword? encoding) encoding :custom)]
                   [true :unicode] BaseFont/IDENTITY_H
                   [true :custom]  (or encoding BaseFont/IDENTITY_H)
                   [true :default] BaseFont/WINANSI
                   BaseFont/WINANSI)

        size     (float (or size 10))

        style    (cond
                   styles (compute-font-style styles)
                   style  (get-style style)
                   :else  Font/NORMAL)

        color    (or (get-color color)
                     (get-color [0 0 0]))
        fnt (FontFactory/getFont ttf encoding true size style color)]
    (when (some? subset?)
      (.setSubset (.getBaseFont fnt) subset?))
    fnt))


(defn create-font-stack ^FontSelector [params ttf-names]
  (let [font-selector (FontSelector.)]
    (doseq [ttf-name ttf-names]
      (.addFont font-selector (font (assoc params :ttf-name ttf-name))))
    font-selector))


(defn flatten-seqs [elements]
  (mapcat (fn [el]
            (if (seq? el)
              (flatten-seqs el)
              (list el)))
          elements))
