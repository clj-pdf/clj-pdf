(ns clj-pdf.graphics-2d
  (:require [clojure.java.io :as io]
            [clojure.string :as str])
  (:import
    [java.awt Graphics2D]
    [cljpdf.text.pdf DefaultFontMapper PdfWriter]
    cljpdf.text.Rectangle))

(declare g2d-register-fonts)
(def g2d-fonts-registered? (atom nil))
(def default-font-mapper (DefaultFontMapper.))

(defn with-graphics [{:keys [^PdfWriter pdf-writer page-width page-height font-mapper under translate rotate scale] :as meta} f]
  (let [font-mapper (or font-mapper default-font-mapper)
        template    (if under
                      (.getDirectContentUnder pdf-writer)
                      (.getDirectContent pdf-writer))
        g2d         (.createGraphics template page-width page-height font-mapper)]
    (try
      (when (coll? translate)
        (.translate g2d (double (first translate)) (double (second translate))))
      (when (number? translate)
        (.translate g2d (double translate) (double translate)))
      (when (coll? scale)
        (.scale g2d (double (first scale)) (double (second scale))))
      (when (number? scale)
        (.scale g2d (double scale) (double scale)))
      (when rotate
        (.rotate g2d (double rotate)))

      (f g2d)
      (Rectangle. 0.0 0.0) ; choose a better placeholder?
      (finally
        (.dispose g2d)))))

(def common-font-dirs
  [["/Library/Fonts", true]
   ["/System/Library/Fonts", false]
   ["c:/windows/fonts", false]
   ["c:/winnt/fonts", false]
   ["d:/windows/fonts", false]
   ["d:/winnt/fonts", false]
   ["/usr/share/X11/fonts", true]
   ["/usr/X/lib/X11/fonts", true]
   ["/usr/openwin/lib/X11/fonts", true]
   ["/usr/share/fonts", true]
   ["/usr/X11R6/lib/X11/fonts", true]])

;;; Other common font dirs, the boolean indicates whether recursive descent needed
   ;; 

(defn- full-path [parent filename]
  (str/join [parent "/" filename]))

(defn g2d-register-fonts []
  (when (nil? @g2d-fonts-registered?)
    (doseq [[dir recursive] common-font-dirs]
      ;; We require to start with a dirctory, so register it.
      (.insertDirectory default-font-mapper dir)
      (if recursive
        (let [init-paths (map #(full-path dir %) (.list (io/file dir)))]
          (loop [maybe-subdir (first init-paths)
                 rest-paths (rest init-paths)]
            (if-let [fd (io/file maybe-subdir)]
              (if (.isDirectory fd)
                (let [loop-paths (map #(full-path maybe-subdir %) (.list fd))]
                  (.insertDirectory default-font-mapper maybe-subdir)
                  (recur (first loop-paths) (rest loop-paths)))
                (recur (first rest-paths) (rest rest-paths))
                ))))))
    (reset! g2d-fonts-registered? true)))

;;; Utility functions

(defn get-font-maps
  "Returns a map with :mapper and :aliases keys, each with a Java HashMap as a val. 
  :mapper connects available AWT font names each to a PDF font object.
  :aliases connects alternate names each to a AWT font name. 
  Names (strings) in either HashMap can be used in a :graphics element's .setFont directive.
  Will register common system font directories if not already registered."
  []
  (g2d-register-fonts)
  {:mapper (.getMapper default-font-mapper)
   :aliases (.getAliases default-font-mapper)})
