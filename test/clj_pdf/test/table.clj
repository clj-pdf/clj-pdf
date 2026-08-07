(ns clj-pdf.test.table
  (:require [clojure.test :refer [deftest is testing]]
            [clj-pdf.section :refer [make-section]])
  (:import [com.lowagie.text Cell Element Table]
           [com.lowagie.text.alignment VerticalAlignment]))

(defn- ^Table table [& body]
  (make-section {} (into [:table] body)))

(deftest table-alignment
  (testing "explicit :align is applied to the table"
    (is (= Element/ALIGN_LEFT (.getAlignment (table {:align :left} ["foo" "bar"]))))
    (is (= Element/ALIGN_CENTER (.getAlignment (table {:align :center} ["foo" "bar"]))))
    (is (= Element/ALIGN_RIGHT (.getAlignment (table {:align :right} ["foo" "bar"]))))
    (is (= Element/ALIGN_JUSTIFIED (.getAlignment (table {:align :justified} ["foo" "bar"])))))

  (testing "omitting :align leaves the openpdf default alone"
    (is (= Element/ALIGN_CENTER (.getAlignment (table {} ["foo" "bar"]))))
    (is (= Element/ALIGN_CENTER (.getAlignment (table {:width 50} ["foo" "bar"]))))))

(deftest table-header-rows
  (testing "a table without a header has no repeating header row"
    (is (= -1 (.getLastHeaderRow (table {} ["foo" "bar"])))))

  (testing "a table with a header repeats just the header"
    (is (= 0 (.getLastHeaderRow (table {:header ["h1" "h2"]} ["foo" "bar"])))))

  (testing ":align does not leak into the header row count"
    (is (= -1 (.getLastHeaderRow (table {:align :right} ["foo" "bar"]))))
    (is (= 0 (.getLastHeaderRow (table {:align :right :header ["h1" "h2"]} ["foo" "bar"]))))))

(deftest cell-vertical-alignment
  (testing "known :valign values are applied"
    (is (= (.getId VerticalAlignment/TOP)
           (.getVerticalAlignment ^Cell (make-section {} [:cell {:valign :top} "foo"]))))
    (is (= (.getId VerticalAlignment/BOTTOM)
           (.getVerticalAlignment ^Cell (make-section {} [:cell {:valign :bottom} "foo"])))))

  (testing "an unrecognized :valign falls back to undefined rather than throwing"
    (is (= (.getId VerticalAlignment/UNDEFINED)
           (.getVerticalAlignment ^Cell (make-section {} [:cell {:valign :nonsense} "foo"]))))))
