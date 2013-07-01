(ns clj_pdf.charting  
  (:import [org.jfree.chart ChartFactory ChartFrame JFreeChart ChartUtilities]
            [org.jfree.chart.plot CategoryPlot PlotOrientation]
            [org.jfree.data.xy XYDataset XYSeries XYSeriesCollection]
            org.jfree.data.category.DefaultCategoryDataset
            org.jfree.data.general.DefaultPieDataset
            org.jfree.chart.renderer.category.StandardBarPainter
            org.jfree.chart.labels.StandardXYItemLabelGenerator
            org.jfree.chart.axis.NumberTickUnit
            java.text.SimpleDateFormat
            java.text.NumberFormat
            [javax.swing JLabel JFrame ]))

(defn- bar-chart [{title       :title
                   horizontal? :horizontal
                   x-label     :x-label
                   y-label     :y-label} & data]
  (let [dataset (new DefaultCategoryDataset)]
    (doseq [[val name] data]
      (.setValue dataset (double val) y-label name))
    (let [chart (ChartFactory/createBarChart title x-label y-label dataset 
                                             (if horizontal? PlotOrientation/HORIZONTAL PlotOrientation/VERTICAL) 
                                             true true false)]       
      (.. chart getCategoryPlot getRenderer (setBarPainter (new StandardBarPainter)))
      chart)))

(defn- pie-chart [{title :title} & data]
  (let [dataset (new DefaultPieDataset)]
    (doseq [[name value] data]
      (.setValue dataset name (double value)))
    (ChartFactory/createPieChart title dataset true true false)))

(defn- set-range [axis range-start range-end tick-interval]
  (.setRange axis range-start range-end)
  (.setTickUnit axis (new NumberTickUnit (or tick-interval 0.1))))

(defn- line-chart [{title         :title
                    points?       :show-points
                    point-labels? :point-labels
                    percision     :label-percision
                    horizontal?   :horizontal
                    time?         :time-series
                    time-format   :time-format 
                    label-format  :label-format
                    x-label       :x-label
                    y-label       :y-label
                    tick-interval :tick-interval
                    [range-start
                     range-end]   :range} & data]
  (let [dataset   (new XYSeriesCollection)
        formatter (if time? (new SimpleDateFormat 
                                 (or time-format "yyyy-MM-dd-HH:mm:ss")))]
    (doseq [[series-title & points] data]
      (let [series (new XYSeries series-title)]
        (doseq [[x y] points]
          (.add series 
            (if time? (.. formatter (parse x) getTime) (double x)) 
            (double y)))
        (.addSeries dataset series)))
    
    (let [chart    (if time? 
                     (ChartFactory/createTimeSeriesChart title x-label y-label dataset true true false)
                     (ChartFactory/createXYLineChart title x-label y-label dataset 
                                                     (if horizontal? 
                                                       PlotOrientation/HORIZONTAL
                                                       PlotOrientation/VERTICAL) true true false))
          plot     (.getPlot chart)
          renderer (.getRenderer plot)]
            
      (if range-end
        (let [domain-axis (.getDomainAxis plot)
              range-axis (.getRangeAxis plot)]
          (set-range domain-axis range-start range-end tick-interval)
          (set-range range-axis range-start range-end tick-interval)))
      
      (if time? (.. plot getDomainAxis (setDateFormatOverride formatter)))
      (if points? (.setBaseShapesVisible renderer true))
      (if point-labels?
        (let [format (NumberFormat/getNumberInstance)]
          (if percision (.setMaximumFractionDigits format (int percision)))
          (.setBaseItemLabelGenerator renderer
            (new StandardXYItemLabelGenerator (or label-format "{1},{2}") format format))
          (.setBaseItemLabelsVisible renderer true)))      
      chart)))



(defn chart [params & items]
  (let [{type   :type
         width  :page-width
         height :page-height} params         
        out (new java.io.ByteArrayOutputStream)
        chart (condp = (when type (name type))
                "bar-chart"  (apply bar-chart params items)
                "pie-chart"  (apply pie-chart params items)
                "line-chart" (apply line-chart params items)
                (throw (new Exception (str "Unsupported chart type " type))))]
    
    (ChartUtilities/writeScaledChartAsPNG out chart (int width) (int height) 2 2)
    (.toByteArray out)))