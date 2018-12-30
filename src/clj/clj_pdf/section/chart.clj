(ns clj-pdf.section.image
  (:require [clj-pdf.section :refer [*cache* render]]
            [clj-pdf.utils :refer [get-alignment]]
            [clj-pdf.charting :as charting])
  (:import [com.lowagie.text Image]
           [java.awt Toolkit]
           [java.net URL]
           [org.apache.commons.codec.binary Base64]))


(defn load-image [img-data base64]
  (cond
    (instance? java.awt.Image img-data)
    (Image/getInstance (.createImage (Toolkit/getDefaultToolkit)
                         (.getSource ^java.awt.Image img-data))
      nil)

    base64
    (Image/getInstance (.createImage (Toolkit/getDefaultToolkit)
                         (^bytes Base64/decodeBase64 ^String img-data))
      nil)

    (= Byte/TYPE (.getComponentType (class img-data)))
    (Image/getInstance (.createImage (Toolkit/getDefaultToolkit)
                         ^bytes img-data)
      nil)

    (string? img-data)
    (Image/getInstance ^String img-data)

    (instance? URL img-data)
    (Image/getInstance ^URL img-data)

    :else
    (throw (new Exception (str "Unsupported image data: " img-data ", must be one of java.net.URL, java.awt.Image, or filename string")))))


(defn- make-image [{:keys [scale
                           xscale
                           yscale
                           align
                           width
                           height
                           base64
                           rotation
                           annotation
                           pad-left
                           pad-right
                           left-margin
                           right-margin
                           top-margin
                           bottom-margin
                           page-width
                           page-height]}
                   img-data]
  (let [^Image img (load-image img-data base64)
        img-width  (.getWidth img)
        img-height (.getHeight img)]

    (if rotation (.setRotation img (float rotation)))
    (if align (.setAlignment img ^int (get-alignment align)))
    (if annotation (.setAnnotation img (apply render :annotation annotation)))
    (if pad-left (.setIndentationLeft img (float pad-left)))
    (if pad-right (.setIndentationRight img (float pad-right)))

    ;;scale relative to page size
    (if (and page-width page-height left-margin right-margin top-margin bottom-margin)
      (let [available-width  (- page-width (+ left-margin right-margin))
            available-height (- page-height (+ top-margin bottom-margin))
            page-scale       (* 100
                                (cond
                                  (and (> img-width available-width)
                                       (> img-height available-height))
                                  (if (> img-width img-height)
                                    (/ available-width img-width)
                                    (/ available-height img-height))

                                  (> img-width available-width)
                                  (/ available-width img-width)

                                  (> img-height available-height)
                                  (/ available-height img-height)

                                  :else 1))]
        (cond
          (and xscale yscale) (.scalePercent img (float (* page-scale xscale)) (float (* page-scale yscale)))
          xscale (.scalePercent img (float (* page-scale xscale)) (float 100))
          yscale (.scalePercent img (float 100) (float (* page-scale yscale)))
          :else (when (or (> img-width available-width) (> img-height available-height))
                  (.scalePercent img (float page-scale))))))

    (if width (.scaleAbsoluteWidth img (float width)))
    (if height (.scaleAbsoluteHeight img (float height)))
    (if scale (.scalePercent img scale))
    img))


(defmethod render :image [tag & [meta img-data :as params]]
  (let [image-hash (.hashCode params)]
    (if-let [cached (get @*cache* image-hash)]
      cached
      (let [compiled (make-image meta img-data)]
        (swap! *cache* assoc image-hash compiled)
        compiled))))


(defn- make-chart [& [meta :as params]]
  (let [{:keys [vector align width height page-width page-height]} meta]
    (if vector
      (apply charting/chart params)
      (render :image
        (cond-> meta
          (not align)              (assoc :align :center)
          (not (and width height)) (assoc :width (* 0.85 page-width)
                                     :height (* 0.85 page-height)))
        (apply charting/chart params)))))


(defmethod render :chart [tag & params]
  (let [chart-hash (.hashCode params)]
    (if-let [cached (get @*cache* chart-hash)]
      cached
      (let [compiled (apply make-chart params)]
        (swap! *cache* assoc chart-hash compiled)
        compiled))))
