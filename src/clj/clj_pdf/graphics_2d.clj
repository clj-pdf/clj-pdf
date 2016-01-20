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
      (when-let [[^double dx ^double dy] translate] (.translate g2d dx dy))
      (when (coll? scale) (.scale g2d (first scale) (second scale)))
      (when (number? scale) (.scale g2d scale scale))
      (when rotate (.rotate g2d rotate))

      (f g2d)
      (Rectangle. 0.0 0.0) ; choose a better placeholder?
      (finally
        (.dispose g2d)))))
