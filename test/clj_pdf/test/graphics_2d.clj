(ns clj-pdf.test.graphics-2d
  (:use clj-pdf.core clj-pdf.graphics-2d clojure.test clojure.java.io)
  (:require [clojure.string :as re])
  (:import [java.awt Font Graphics2D]
           [cljpdf.text.pdf DefaultFontMapper]
           [cljpdf.text FontFactory]))

;; Utilities for the test.  Is there a better place to put these shared defns?

(defn add-test-path-prefix [filename]
  (str "test" java.io.File/separator filename))

(defn fix-pdf [^bytes pdf-bytes]
  (-> (String. pdf-bytes)
      ; obviously these will get changed on each run, so strip the creation/modification date/times
      (re/replace #"ModDate\((.*?)\)" "")
      (re/replace #"CreationDate\((.*?)\)" "")
      ; not sure what this is for ... ?
      (re/replace #"\[(.*?)\]" "")
      ; these are kind of hacky, but it seems that the prefix characters before the font name "Carlito"
      ; will get randomly generated on each run ... ?
      (re/replace #"Font\/([A-Z]+\+Carlito)" "Font/AQZVXW+Carlito")
      (re/replace #"FontBBox\/([A-Z]+\+Carlito)" "FontBBox/AQZVXW+Carlito")
      (re/replace #"FontName\/([A-Z]+\+Carlito)" "FontName/AQZVXW+Carlito")
      (re/replace #"BaseFont\/([A-Z]+\+Carlito)" "BaseFont/AQZVXW+Carlito")))

(defn read-file ^bytes [file-path]
  (with-open [reader (input-stream file-path)]
    (let [length (.length (file file-path))
          buffer (byte-array length)]
      (.read reader buffer 0 length)
      buffer)))

(defn pdf->bytes ^bytes [doc]
  (let [out (new java.io.ByteArrayOutputStream)]
    (pdf doc out)
    (.toByteArray out)))

;; MODIFIED from clj_pdf/test/core.clj.  Pulled out font-filename from let binding to use later.
(def font-filename (add-test-path-prefix "Carlito-Regular.ttf"))
(defn inject-test-font [doc]
  (let [font-props    {:encoding :unicode
                       :ttf-name font-filename}]
    (update-in (vec doc) [0 :font] merge font-props)))

(defn generate-pdf [doc output-filename]
  (let [doc1 (inject-test-font doc)]
    (println "regenerating pdf" output-filename)
    (pdf doc1 (add-test-path-prefix output-filename))
    true))

(defn eq? [doc1 filename]
  (let [filename   (add-test-path-prefix filename)
        doc1       (inject-test-font doc1)
        doc1-bytes (pdf->bytes doc1)
        doc2-bytes (read-file filename)]
    (is (= (fix-pdf doc1-bytes)
           (fix-pdf doc2-bytes)))))

(defn regenerate-test-pdfs []
  (with-redefs [eq? generate-pdf]
    (run-tests)))

; run this to regenerate all test pdfs
#_(regenerate-test-pdfs)

#_(run-tests)

(def test-directory (.getAbsolutePath (file "test")))

(with-redefs [common-font-dirs [[test-directory true]]]
  (g2d-register-fonts))

(def font-file (clojure.java.io/file font-filename))
(def AWT-CARLITO-BASE (java.awt.Font/createFont java.awt.Font/TRUETYPE_FONT font-file))
(def AWT-CARLITO (.deriveFont AWT-CARLITO-BASE (float 18)))

(deftest setFont
  (eq?
   [{:title         "Graphics setFont test doc"
     :left-margin   10
     :right-margin  50
     :top-margin    20
     :bottom-margin 25
     :font          {:size 12}
     :size          :a4
     :orientation   "portrait"
     :register-system-fonts? true}
    [:chapter "Graphics2D tests"]
    [:heading "setFont"]
    [:paragraph "This test substitutes a single font, Carlito, for all system fonts.  Carlito is an open source font. Carlito-Regular.ttf is included in this repo.  Usage note: evaluate (clj-pdf.graphics2d/get-font-maps) to see available system fonts and their exact names.  In a pinch, the Java default font names are: Serif, SansSerif, Monospaced, Dialog, and DialogInput."]
    [:paragraph "The font system for Graphics2D, invoked with the :graphics tag, is different than that used in the rest of clj-pdf. Enabling ':register-system-fonts? true' in the document metadata will also register system fonts for use with Graphics2D's .setFont."]
    [:graphics {:under false :translate [100 600] :rotate (/ Math/PI -4)}
     (fn [g2d]
       (.setFont g2d AWT-CARLITO)
       (.drawString g2d "This paragraph is drawn with a :graphics tag and (.drawString...)." (float 0) (float 0))
       (.drawString g2d "As such, it can be rotated and placed arbitrarily." (float 0) (float 24))
       (.drawString g2d "It should be set in Carlito, and have an 18 point font size." (float 0) (float 48)))]]
   
   "graphics.pdf"))
