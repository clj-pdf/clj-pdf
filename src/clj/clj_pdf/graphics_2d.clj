(ns clj-pdf.graphics-2d
  (:import cljpdf.text.pdf.DefaultFontMapper
           cljpdf.text.Rectangle))

(defn with-graphics [{:keys [^cljpdf.text.pdf.PdfWriter pdf-writer page-width page-height font-mapper under translate rotate scale] :as meta} f]
  (let [font-mapper (or font-mapper (DefaultFontMapper.))
        template    (if under
                      (.getDirectContentUnder pdf-writer)
                      (.getDirectContent pdf-writer))
        ^java.awt.Graphics2D g2d (.createGraphics template page-width page-height font-mapper)]
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
