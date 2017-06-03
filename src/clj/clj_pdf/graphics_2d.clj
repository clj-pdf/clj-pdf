(ns clj-pdf.graphics-2d
  (:import
    [java.awt Graphics2D]
    [cljpdf.text.pdf DefaultFontMapper PdfWriter]
    cljpdf.text.Rectangle))

(defn with-graphics [{:keys [^PdfWriter pdf-writer page-width page-height font-mapper under translate rotate scale] :as meta} f]
  (let [font-mapper (or font-mapper (DefaultFontMapper.))
        template    (if under
                      (.getDirectContentUnder pdf-writer)
                      (.getDirectContent pdf-writer))
        g2d         (.createGraphics template page-width page-height font-mapper)]
    (.insertDirectory font-mapper "/Library/Fonts")
    (.insertDirectory font-mapper "/System/Library/Fonts")
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
