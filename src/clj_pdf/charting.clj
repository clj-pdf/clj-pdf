(ns clj_pdf.charting
  (:import [org.jfree.chart ChartFactory ChartFrame JFreeChart]
            [org.jfree.chart.plot CategoryPlot PlotOrientation]
            [org.jfree.data.xy XYDataset XYSeries XYSeriesCollection]
            org.jfree.data.category.DefaultCategoryDataset
            org.jfree.data.general.DefaultPieDataset
            org.jfree.chart.renderer.category.StandardBarPainter
            java.awt.Image
            [javax.swing JLabel JFrame ]))


(defn- bar-chart [{title   :title
                   x-label :x-label
                   y-label :y-label} & data]
  (let [dataset (new DefaultCategoryDataset)]
    (doseq [[val name] data]
      (.setValue dataset (double val) y-label name))
    (let [chart (ChartFactory/createBarChart title x-label y-label dataset PlotOrientation/VERTICAL true true false)]       
      (.. chart getCategoryPlot getRenderer (setBarPainter (new StandardBarPainter)))
      chart)))

(defn- pie-chart [{title :title} & data]
  (let [dataset (new DefaultPieDataset)]
    (doseq [[name value] data]
      (.setValue dataset name (double value)))
    (ChartFactory/createPieChart title dataset true true false)))

(defn- line-chart [{title :title
                    x-label :x-label
                    y-label :y-label} & data]
  (let [dataset (new XYSeriesCollection)]
    (doseq [[series-title & points] data]
      (let [series (new XYSeries series-title)]
        (doseq [[x y] points]
          (.add series (double x) (double y)))
        (.addSeries dataset series)))
    (ChartFactory/createXYLineChart title x-label y-label dataset PlotOrientation/VERTICAL true true false)))

(defn chart [params & items]
  (.createBufferedImage
    (condp = (:type params)
      "bar-chart"  (apply bar-chart params items)
      "pie-chart"  (apply pie-chart params items)
      "line-chart" (apply line-chart params items))
    1000 1000))
