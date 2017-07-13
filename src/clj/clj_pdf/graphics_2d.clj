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

(defn- full-path [parent filename]
  (str/join [parent java.io.File/separator filename]))

(defn g2d-register-fonts-helper [dir recursive]
  (.insertDirectory default-font-mapper dir)
  (if recursive
    (let [fd (io/file dir)
          files (.list fd)
          paths (map #(full-path dir %) files)]
      (doseq [path paths]
        (if (.isDirectory (io/file path))
          (g2d-register-fonts-helper
           path recursive))))))

(defn g2d-register-fonts
  "Walks common font directories and registers them for use. Optionally 
  accepts a coll of absolute directories to register each with a 
  subdirectory walk directive, in the form of 'common-font-dirs'. Eval
  with custom fonts solely to override system fonts.  Eval empty first
  then again with custom fonts to augment system fonts."
  [& [font-dirs]]
  ;; This is guarded by a stateful atom in addition to core/fonts-registered?
  ;; because get-font-maps can also trigger a g2d-register-fonts. For a big
  ;; font library, registration can be slow.
  (when (or (nil? @g2d-fonts-registered?)
            font-dirs)
    (doseq [[dir recursive] (or font-dirs common-font-dirs)]
      (g2d-register-fonts-helper dir recursive))
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
