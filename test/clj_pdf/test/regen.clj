(ns clj-pdf.test.regen
  (:require [clj-pdf.test.core]
            [clj-pdf.test.utils :refer [eq? pdf->bytes regenerate-test-pdfs]]
            [clj-pdf.test.graphics-2d]
            [clj-pdf.section.chart]))

(defn -main [& _]
  (regenerate-test-pdfs 'clj-pdf.test.core 'clj-pdf.test.graphics-2d))
