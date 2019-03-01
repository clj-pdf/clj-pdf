(ns clj-pdf.section.cell
  (:require [clj-pdf.utils :refer [get-color get-alignment]]
            [clj-pdf.section :refer [render make-section-or]])
  (:import [com.lowagie.text Cell Rectangle]
           [com.lowagie.text.pdf PdfPCell PdfPCellEvent PdfPTable PdfContentByte]))


(defn- get-border [borders]
  (reduce +
    (vals
      (select-keys
        {:top Cell/TOP :bottom Cell/BOTTOM :left Cell/LEFT :right Cell/RIGHT}
        borders))))


(defmethod render :cell
  [_ {:keys [background-color
             colspan
             rowspan
             border
             align
             valign
             leading
             set-border
             border-color
             border-width
             border-width-bottom
             border-width-left
             border-width-right
             border-width-top] :as meta}
   & content]

  (let [c (Cell.)]

    (when-let [color (get-color background-color)]
      (.setBackgroundColor c color))

    (when-let [color (get-color border-color)]
      (.setBorderColor c color))

    (when (some? border)
      (.setBorder c (if border Rectangle/BOX Rectangle/NO_BORDER)))

    (when rowspan (.setRowspan c (int rowspan)))
    (when colspan (.setColspan c (int colspan)))
    (when set-border (.setBorder c (int (get-border set-border))))
    (when border-width (.setBorderWidth c (float border-width)))
    (when border-width-bottom (.setBorderWidthBottom c (float border-width-bottom)))
    (when border-width-left (.setBorderWidthLeft c (float border-width-left)))
    (when border-width-right (.setBorderWidthRight c (float border-width-right)))
    (when border-width-top (.setBorderWidthTop c (float border-width-top)))
    (when valign (.setVerticalAlignment c ^int (get-alignment valign)))
    (when leading (.setLeading c (float leading)))
    (.setHorizontalAlignment c ^int (get-alignment align))

    (doseq [item content]
      (.addElement c (make-section-or :chunk meta item)))

    c))


(defn- pdf-cell-padding*
  ([^PdfPCell cell a] (.setPadding cell (float a)))
  ([^PdfPCell cell a b] (pdf-cell-padding* cell a b a b))
  ([^PdfPCell cell a b c] (pdf-cell-padding* cell a b c b))
  ([^PdfPCell cell a b c d]
   (doto cell
     (.setPaddingTop (float a))
     (.setPaddingRight (float b))
     (.setPaddingBottom (float c))
     (.setPaddingLeft (float d)))))


(defn- pdf-cell-padding [^PdfPCell cell pad]
  (if (sequential? pad)
    (apply pdf-cell-padding* cell pad)
    (pdf-cell-padding* cell pad)))

(defmacro ^:private with-saved-canvas [bindings & body]
  `(let ~bindings
     (.saveState ~(bindings 0))
     ~@body
     (.restoreState ~(bindings 0))))

(defn- make-event-handler [base-layer-fn background-layer-fn text-layer-fn line-layer-fn]
  (proxy [PdfPCellEvent] []
   (cellLayout [^PdfPCell _cell ^Rectangle position canvases]
     (when base-layer-fn
       (with-saved-canvas [^PdfContentByte canvas (aget canvases PdfPTable/BASECANVAS)]
                          (base-layer-fn position canvas)))
     (when background-layer-fn
       (with-saved-canvas [^PdfContentByte canvas (aget canvases PdfPTable/BACKGROUNDCANVAS)]
                          (background-layer-fn position canvas)))
     (when text-layer-fn
       (with-saved-canvas [^PdfContentByte canvas (aget canvases PdfPTable/TEXTCANVAS)]
                          (text-layer-fn position canvas)))
     (when line-layer-fn
       (with-saved-canvas [^PdfContentByte canvas (aget canvases PdfPTable/LINECANVAS)]
                          (line-layer-fn position canvas))))))

(defmethod render :pdf-cell
  [_ {:keys [background-color
             colspan
             rowspan
             border
             align
             valign
             set-border
             border-color
             border-width
             border-width-bottom
             border-width-left
             border-width-right
             border-width-top
             padding
             padding-bottom
             padding-left
             padding-right
             padding-top
             rotation
             height
             min-height
             base-layer-fn
             background-layer-fn
             text-layer-fn
             line-layer-fn
             event-handler] :as meta}
   & content]

  (let [c (PdfPCell.)]

    (when-let [color (get-color background-color)]
      (.setBackgroundColor c color))

    (when-let [color (get-color border-color)]
      (.setBorderColor c color))

    (when (not (nil? border))
      (.setBorder c (if border Rectangle/BOX Rectangle/NO_BORDER)))

    (when rowspan (.setRowspan c (int rowspan)))
    (when colspan (.setColspan c (int colspan)))
    (when set-border (.setBorder c (int (get-border set-border))))
    (when border-width (.setBorderWidth c (float border-width)))
    (when border-width-bottom (.setBorderWidthBottom c (float border-width-bottom)))
    (when border-width-left (.setBorderWidthLeft c (float border-width-left)))
    (when border-width-right (.setBorderWidthRight c (float border-width-right)))
    (when border-width-top (.setBorderWidthTop c (float border-width-top)))
    (when padding (pdf-cell-padding c padding))
    (when padding-bottom (.setPaddingBottom c (float padding-bottom)))
    (when padding-left (.setPaddingLeft c (float padding-left)))
    (when padding-right (.setPaddingRight c (float padding-right)))
    (when padding-top (.setPaddingTop c (float padding-top)))
    (when rotation (.setRotation c (int rotation)))
    (when height (.setFixedHeight c (float height)))
    (when min-height (.setMinimumHeight c (float min-height)))
    (.setHorizontalAlignment c ^int (get-alignment align))
    (.setVerticalAlignment c ^int (get-alignment valign))

    (if event-handler
      (.setCellEvent c ^PdfPCellEvent event-handler)
      (when (some some? [base-layer-fn background-layer-fn text-layer-fn line-layer-fn])
        (.setCellEvent c ^PdfPCellEvent (make-event-handler base-layer-fn background-layer-fn text-layer-fn line-layer-fn))))

    (doseq [item content]
      (.addElement c (make-section-or :paragraph meta item)))

    c))
