(ns clj-pdf.test.fonts
  (:require [clojure.test :refer [deftest is testing]]
            [clj-pdf.core :refer [pdf]]
            [clj-pdf.utils :refer [font]])
  (:import [com.lowagie.text Font FontFactory]
           [java.io ByteArrayOutputStream]))

(def ttf "test/Carlito-Regular.ttf")

(defn- base-font [opts]
  (.getBaseFont ^Font (font (merge {:ttf-name ttf :encoding :unicode} opts))))

(deftest font-flags-do-not-leak
  (testing "subset? on one font does not change fonts that don't set it"
    (is (false? (.isSubset (base-font {:subset? false}))))
    (is (true? (.isSubset (base-font {})))))
  (testing "include-cid-set? on one font does not change fonts that don't set it"
    (is (false? (.isIncludeCidSet (base-font {:subset? true :include-cid-set? false}))))
    (is (true? (.isIncludeCidSet (base-font {})))))
  (testing "a PDF/A-3a document does not change fonts in later documents"
    (pdf [{:pdfa-compliance :3a :title "t" :language "en"
           :font {:ttf-name ttf :encoding :unicode}}
          "hello"]
         (ByteArrayOutputStream.))
    (is (true? (.isSubset (base-font {}))))))

(deftest fonts-with-same-settings-share-base-font
  (is (identical? (base-font {:size 10}) (base-font {:size 12 :styles [:bold]})))
  (is (identical? (base-font {:subset? false}) (base-font {:subset? false :size 14})))
  (is (not (identical? (base-font {}) (base-font {:subset? false})))))

(deftest font-embedded-once-per-document
  (let [doc [{:font {:ttf-name ttf :encoding :unicode :subset? false}}
             (into [:table {:header ["a" "b" "c"]}] (repeat 50 ["x" "y" "z"]))]
        out (ByteArrayOutputStream.)]
    (pdf doc out)
    ;; one full embedded copy of Carlito is ~275KB; a copy per cell would be several MB
    (is (< (.size out) 400000))))

(deftest registered-font-aliases-still-resolve
  (FontFactory/register ttf "clj-pdf-test-carlito")
  (let [bf (.getBaseFont ^Font (font {:ttf-name "clj-pdf-test-carlito" :encoding :unicode :subset? false}))]
    (is (some? bf))
    (is (false? (.isSubset bf)))
    (is (= "Carlito" (.getPostscriptFontName bf)))))
