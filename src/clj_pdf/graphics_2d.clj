(ns clj-pdf.graphics-2d
  (:import [com.lowagie.text.pdf PdfTemplate PdfGraphics2D FontMapper DefaultFontMapper]
           [com.lowagie.text Rectangle]))

(defn with-graphics [{:keys [pdf-writer page-width page-height font-mapper under translate rotate scale] :as meta} f]
  (let [font-mapper (or font-mapper (DefaultFontMapper.))
        template    (if under
                      (.getDirectContentUnder pdf-writer)
                      (.getDirectContent pdf-writer))
        g2d         (.createGraphics template page-width page-height font-mapper)]
    (try
      (when-let [[dx dy] translate] (.translate g2d dx dy))
      (when (coll? scale) (.scale g2d (first scale) (second scale)))
      (when (number? scale) (.scale g2d scale scale))
      (when rotate (.rotate g2d rotate))

      (f g2d)
      (Rectangle. 0.0 0.0) ; choose a better placeholder?
      (finally
        (.dispose g2d)))))
