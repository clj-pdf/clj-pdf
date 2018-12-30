(ns clj-pdf.utils
  (:require [clojure.string :refer [split]])
  (:import [java.awt Color]
           [com.lowagie.text Element Font FontFactory]
           [com.lowagie.text.pdf BaseFont]))


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
  [{style    :style
    styles   :styles
    size     :size
    color    :color
    family   :family
    ttf-name :ttf-name
    encoding :encoding}]

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
                     (get-color [0 0 0]))]

    (FontFactory/getFont ttf encoding true size style color)))

