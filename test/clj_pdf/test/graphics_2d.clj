(ns clj-pdf.test.graphics-2d
  (:require [clj-pdf.core :refer []]
            [clj-pdf.graphics-2d :refer [g2d-register-fonts]]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is]]
            [clj-pdf.test.utils :refer [font-filename eq?]]))

(def test-directory (.getAbsolutePath (io/file "test")))

(g2d-register-fonts [[test-directory true]])

(def font-file (io/file font-filename))
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
    [:paragraph "The \"a\" below is drawn using a :graphics tag and (.drawString...).  As such, it can be placed arbitrarily (and could also be rotated).  It should be set in Carlito, and have an 18 point font size.  Unfortunately, there are visually undetectable variations in output between systems that prevent more text from being rendered while allowing for automated testing.  Hence the short string."]
    [:graphics {:under false :translate [70 350]}
     (fn [g2d]
       (.setFont g2d AWT-CARLITO)
       (.drawString g2d "a" (float 0) (float 0)))]]
   "graphics.pdf"
   :stream false))
