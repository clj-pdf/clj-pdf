(ns clj-pdf.charting
  (:use [clj-pdf.graphics-2d :only [with-graphics]])
  (:import [org.jfree.chart ChartFactory ChartUtilities]
           [org.jfree.chart.plot PlotOrientation]
           [org.jfree.data.xy XYSeries XYSeriesCollection]
           org.jfree.data.category.DefaultCategoryDataset
           org.jfree.data.general.DefaultPieDataset
           org.jfree.chart.renderer.category.StandardBarPainter
           org.jfree.chart.labels.StandardXYItemLabelGenerator
           org.jfree.chart.axis.NumberTickUnit
           java.text.SimpleDateFormat
           java.text.NumberFormat
           java.io.ByteArrayOutputStream
           java.awt.Rectangle
           java.awt.Color))

(defn- set-background [chart [r g b]]
  (when (and r g b)
    (-> chart .getPlot (.setBackgroundPaint (Color. (int r) (int g) (int b))))))

(defn- bar-chart [{:keys [title horizontal x-label y-label background]} & data]
  (let [dataset (new DefaultCategoryDataset)]
    (doseq [[val name] data]
      (.setValue dataset (double val) y-label name))
    (let [chart (ChartFactory/createBarChart title x-label y-label dataset
                                             (if horizontal PlotOrientation/HORIZONTAL PlotOrientation/VERTICAL)
                                             true true false)]
      (set-background chart background)
      (.. chart getCategoryPlot getRenderer (setBarPainter (new StandardBarPainter)))
      chart)))

(defn- pie-chart [{:keys [title background]} & data]
  (let [dataset (new DefaultPieDataset)]
    (doseq [[name value] data]
      (.setValue dataset name (double value)))
    (let [chart (ChartFactory/createPieChart title dataset true true false)]
      (set-background chart background)
      chart)))

(defn- line-chart [{:keys [title
                           background
                           show-points
                           point-labels
                           label-percision
                           horizontal
                           time-series
                           time-format
                           label-format
                           x-label
                           y-label
                           tick-interval
                           tick-interval-x
                           tick-interval-y
                           x-range
                           y-range]} & data]
  (let [[xrange-start xrange-end] x-range
        [yrange-start yrange-end] y-range
        dataset   (new XYSeriesCollection)
        formatter (if time-series (new SimpleDateFormat
                                 (or time-format "yyyy-MM-dd-HH:mm:ss")))]
    (doseq [[series-title & points] data]
      (let [series (new XYSeries series-title)]
        (doseq [[x y] points]
          (.add series
            (if time-series (.. formatter (parse x) getTime) (double x))
            (double y)))
        (.addSeries dataset series)))

    (let [chart    (if time-series
                     (ChartFactory/createTimeSeriesChart title x-label y-label dataset true true false)
                     (ChartFactory/createXYLineChart title x-label y-label dataset
                                                     (if horizontal
                                                       PlotOrientation/HORIZONTAL
                                                       PlotOrientation/VERTICAL) true true false))
          plot     (.getPlot chart)
          renderer (.getRenderer plot)]

      (set-background chart background)

      (let [domain-axis (.getDomainAxis plot)
            range-axis (.getRangeAxis plot)]
        (if (or tick-interval tick-interval-x)
          (.setTickUnit domain-axis (new NumberTickUnit (or tick-interval tick-interval-x))))

        (if (or tick-interval tick-interval-y)
          (.setTickUnit range-axis (new NumberTickUnit (or tick-interval tick-interval-y))))

          (if xrange-end (.setRange domain-axis xrange-start xrange-end))
          (if yrange-end (.setRange range-axis yrange-start yrange-end)))

      (if time-series (.. plot getDomainAxis (setDateFormatOverride formatter)))
      (if show-points (.setBaseShapesVisible renderer true))
      (if point-labels
        (let [format (NumberFormat/getNumberInstance)]
          (if label-percision (.setMaximumFractionDigits format (int label-percision)))
          (.setBaseItemLabelGenerator renderer
            (new StandardXYItemLabelGenerator (or label-format "{1},{2}") format format))
          (.setBaseItemLabelsVisible renderer true)))
      chart)))

(defn chart [{:keys [type page-width page-height vector] :as params} & items]
  (let [chart (condp = (when type (name type))
                "bar-chart"  (apply bar-chart params items)
                "pie-chart"  (apply pie-chart params items)
                "line-chart" (apply line-chart params items)
                (throw (new Exception (str "Unsupported chart type " type))))]

    (if vector
      (with-graphics
        params
        (fn [g2d]
          (.draw chart g2d (Rectangle. 0 0 (:width params) (:height params)))))

      (with-open [out (ByteArrayOutputStream.)]
        (ChartUtilities/writeScaledChartAsPNG out chart (int page-width) (int page-height) 2 2)
        (.toByteArray out)))))
