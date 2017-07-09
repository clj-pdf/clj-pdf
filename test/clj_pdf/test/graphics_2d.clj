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
      (re/replace #"Font\/([A-Z]+\+Carlito)" "Font/PHZPHX+Carlito")
      (re/replace #"FontBBox\/([A-Z]+\+Carlito)" "FontBBox/PHZPHX+Carlito")
      (re/replace #"FontName\/([A-Z]+\+Carlito)" "FontName/PHZPHX+Carlito")
      (re/replace #"BaseFont\/([A-Z]+\+Carlito)" "BaseFont/PHZPHX+Carlito")

      ;; Helvetica didn't have this this prefixing, maybe because it was the default font?
      ;; If you regenerate the graphics-[ubuntu|macos|windows] files, you need to change relevant
      ;; stanzas below to match the random prefix in the newly generated files.

      ;; TimesNewRoman needs three because of the three OS-specific versions 

      ;; For Ubuntu / Lato
      (re/replace #"Font\/([A-Z]+\+TimesNewRomanPSMT)" "Font/YWNCWD+TimesNewRomanPSMT")
      (re/replace #"FontBBox\/([A-Z]+\+TimesNewRomanPSMT)" "FontBBox/YWNCWD+TimesNewRomanPSMT")
      (re/replace #"FontName\/([A-Z]+\+TimesNewRomanPSMT)" "FontName/YWNCWD+TimesNewRomanPSMT")
      (re/replace #"BaseFont\/([A-Z]+\+TimesNewRomanPSMT)" "BaseFont/YWNCWD+TimesNewRomanPSMT")
      (re/replace #"Font\/([A-Z]+\+Lato)" "Font/VCMHTK+Lato-Regular")
      (re/replace #"FontBBox\/([A-Z]+\+Lato)" "FontBBox/VCMHTK+Lato-Regular")
      (re/replace #"FontName\/([A-Z]+\+Lato)" "FontName/VCMHTK+Lato-Regular")
      (re/replace #"BaseFont\/([A-Z]+\+Lato)" "BaseFont/VCMHTK+Lato-Regular")

      ;; For MacOS / Zapfino
      (re/replace #"Font\/([A-Z]+\+TimesNewRomanPSMT)" "Font/AAJOKP+TimesNewRomanPSMT")
      (re/replace #"FontBBox\/([A-Z]+\+TimesNewRomanPSMT)" "FontBBox/AAJOKP+TimesNewRomanPSMT")
      (re/replace #"FontName\/([A-Z]+\+TimesNewRomanPSMT)" "FontName/AAJOKP+TimesNewRomanPSMT")
      (re/replace #"BaseFont\/([A-Z]+\+TimesNewRomanPSMT)" "BaseFont/AAJOKP+TimesNewRomanPSMT")
      (re/replace #"Font\/([A-Z]+\+Zapfino)" "Font/CHIZIR+Zapfino")
      (re/replace #"FontBBox\/([A-Z]+\+Zapfino)" "FontBBox/CHIZIR+Zapfino")
      (re/replace #"FontName\/([A-Z]+\+Zapfino)" "FontName/CHIZIR+Zapfino")
      (re/replace #"BaseFont\/([A-Z]+\+Zapfino)" "BaseFont/CHIZIR+Zapfino")

      ;; For Windows / Palatino
      (re/replace #"Font\/([A-Z]+\+TimesNewRomanPSMT)" "Font/BFYXLP+TimesNewRomanPSMT")
      (re/replace #"FontBBox\/([A-Z]+\+TimesNewRomanPSMT)" "FontBBox/BFYXLP+TimesNewRomanPSMT")
      (re/replace #"FontName\/([A-Z]+\+TimesNewRomanPSMT)" "FontName/BFYXLP+TimesNewRomanPSMT")
      (re/replace #"BaseFont\/([A-Z]+\+TimesNewRomanPSMT)" "BaseFont/BFYXLP+TimesNewRomanPSMT")
      (re/replace #"Font\/([A-Z]+\+Palatino-Roman)" "Font/GVUQGP+Palatino-Roman")
      (re/replace #"FontBBox\/([A-Z]+\+Palatino-Roman)" "FontBBox/GVUQGP+Palatino-Roman")
      (re/replace #"FontName\/([A-Z]+\+Palatino-Roman)" "FontName/GVUQGP+Palatino-Roman")
      (re/replace #"BaseFont\/([A-Z]+\+Palatino-Roman)" "BaseFont/GVUQGP+Palatino-Roman")
      ))

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

(defn inject-test-font [doc]
  (let [font-filename (add-test-path-prefix "Carlito-Regular.ttf")
        font-props    {:encoding :unicode
                       :ttf-name font-filename}]
    (update-in (vec doc) [0 :font] merge font-props)))

(defn generate-pdf [doc output-filename]
  (let [doc1 (inject-test-font doc)]
    (println "regenerating pdf" output-filename)
    (pdf doc1 (add-test-path-prefix output-filename))
                                        ; TODO check what else this will impact
    false                               ; Was 'true'. Changed to 'false' so the 'or' in setFont doesn't short-circuit
    ))

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

;; TODO Other Linuxes: SuSE, CentOS, RedHat, Mint, Debian, Arch, etc.
;; TODO BSD and Solaris.  A naive search in Jul 2017 of FreeBSD 10.3-STABLE-amd64-2017-04-13 on AWS and
;; SmartOS base-64-lts 16.4.1 (a poor man's proxy for Solaris) was done.
;;
;; On FreeBSD 
;; /usr/share/examples/BSD_daemon/FreeBSD.pfa
;; /usr/share/groff_font/devps/symbolsl.pfa
;; /usr/share/groff_font/devps/zapfdr.pfa
;; /usr/share/groff_font/devps/freeeuro.pfa
;;
;; On SmartOS
;; No *.tff|.pfa|.pfb|*.pfm|*.fot|*.fon|*.fnt found
;; (not that the bitmapped .fon .fnt would have helped)
;;

(def para1 "Available fonts vary greatly among operating system and individual installations.  The graphics-2d font capability differs from the rest of clj-pdf, relying on an underlying Java AWT system.  Currently, that system imports fonts only from common system directories (TrueType, OpenType, and Type1 fonts). This makes choosing a single cross-platform test font that isn't in the default set (SansSerif(Helvetica), Serif(Times), Monospaced(Courier)) unlikely.")

(def para2 "To be reasonably cross-platform, these tests work generally under at least one standard operating system font, to the extent that exists at all in a given OS family.  This has been verified on at least one system of the following: MacOS 10.12.5.")

(deftest setFont
  (or
   (eq?
    [{:title         "Graphics setFont test doc Ubuntu"
      :left-margin   10
      :right-margin  50
      :top-margin    20
      :bottom-margin 25
      :font          {:size 12}
      :size          :a4
      :orientation   "portrait"
      :register-system-fonts? true}
     [:chapter "Graphics2D tests"]
     [:heading "setFont Ubuntu"]
     [:paragraph para1]
     [:paragraph para2]
     [:graphics {:under false :translate [30 250]}
      (fn [g2d]
        (.drawString g2d "All systems: This text has its font unspecified, so should have font size 12, and be Helvetica" (float 0) (float 0))
        (.drawString g2d "on most systems. Helvetica is a very widely used sans serif font by Max Miedinger with input" (float 0) (float 18))
        (.drawString g2d "from Eduard Hoffmann.  The Regular weight, which this text should be set in, has a double" (float 0) (float 36))
        (.drawString g2d "story 'a' with a small curved terminal at the bottom of the main vertical stroke.  The 'g'" (float 0) (float 54))
        (.drawString g2d "is single story." (float 0) (float 72)))]
     [:graphics {:under false :translate [30 350]}
      (fn [g2d]
        (.setFont g2d (java.awt.Font. "Times New Roman" java.awt.Font/PLAIN 12))
        (.drawString g2d "All systems: This should have font size 12, and be Times New Roman." (float 0) (float 0))
        (.drawString g2d "Times New Roman is a popular classic serif font by Stanley Morison with input from Victor Lardent," (float 0) (float 18))
        (.drawString g2d "released in 1932.  The Regular weight, which this text should be set in, is a serif font that was" (float 0) (float 36))
        (.drawString g2d "designed for newsprint, optimized for readibility and compactness. It has both double story 'g' and 'a'." (float 0) (float 54)))]
     [:graphics {:under false :translate [30 440]}
      (fn [g2d]
        (.setFont g2d (java.awt.Font. "Lato" java.awt.Font/PLAIN 12))
        (.drawString g2d "Ubuntu: This should have font size 12, and be Lato." (float 0) (float 0))
        (.drawString g2d "Lato is a popular open source font by Lukasz Dziedzic and team.  The Regular (400) weight, which this " (float 0) (float 18)) ;TODO get .drawString to render the Cyrillic 'Ł' in Łukasz
        (.drawString g2d "text should be set in, is a sans serif font with double story 'g' and 'a'.  The 'a' does not have the curved" (float 0) (float 36))
        (.drawString g2d "bottom terminal of Helvetica Regular." (float 0) (float 54)))]]
    "graphics-ubuntu.pdf")
   (eq?
    [{:title         "Graphics setFont test doc MacOS"
      :left-margin   10
      :right-margin  50
      :top-margin    20
      :bottom-margin 25
      :font          {:size 12}
      :size          :a4
      :orientation   "portrait"
      :register-system-fonts? true}
     [:chapter "Graphics2D tests"]
     [:heading "setFont MacOS"]
     [:paragraph para1]
     [:paragraph para2]
     [:graphics {:under false :translate [30 250]}
      (fn [g2d]
        (.drawString g2d "All systems: This text has its font unspecified, so should have font size 12, and be Helvetica" (float 0) (float 0))
        (.drawString g2d "on most systems. Helvetica is a very widely used sans serif font by Max Miedinger with input" (float 0) (float 18))
        (.drawString g2d "from Eduard Hoffmann.  The Regular weight, which this text should be set in, has a double" (float 0) (float 36))
        (.drawString g2d "story 'a' with a small curved terminal at the bottom of the main vertical stroke.  The 'g'" (float 0) (float 54))
        (.drawString g2d "is single story." (float 0) (float 72)))]
     [:graphics {:under false :translate [30 350]}
      (fn [g2d]
        (.setFont g2d (java.awt.Font. "Times New Roman" java.awt.Font/PLAIN 12))
        (.drawString g2d "All systems: This should have font size 12, and be Times New Roman." (float 0) (float 0))
        (.drawString g2d "Times New Roman is a popular classic serif font by Stanley Morison with input from Victor Lardent," (float 0) (float 18))
        (.drawString g2d "released in 1932.  The Regular weight, which this text should be set in, is a serif font that was" (float 0) (float 36))
        (.drawString g2d "designed for newsprint, optimized for readibility and compactness. It has both double story 'g' and 'a'." (float 0) (float 54)))]
     [:graphics {:under false :translate [30 440]}
      (fn [g2d]
        (.setFont g2d (java.awt.Font. "Zapfino" java.awt.Font/PLAIN 12))
        (.drawString g2d "MacOS: This should have font size 12, and be Zapfino." (float 0) (float 0))
        (.drawString g2d "Zapfino is a highly decorative calligraphic font by Hermann Zapf, with" (float 0) (float 40))
        (.drawString g2d "input from Gino Lee and David Siegel." (float 0) (float 80)))]]
    "graphics-macos.pdf")
      (eq?
    [{:title         "Graphics setFont test doc Windows" 
      :left-margin   10
      :right-margin  50
      :top-margin    20
      :bottom-margin 25
      :font          {:size 12}
      :size          :a4
      :orientation   "portrait"
      :register-system-fonts? true}
     [:chapter "Graphics2D tests"]
     [:heading "setFont Windows"]
     [:paragraph para1]
     [:paragraph para2]
     [:graphics {:under false :translate [30 250]}
      (fn [g2d]
        (.drawString g2d "All systems: This text has its font unspecified, so should have font size 12, and be Helvetica" (float 0) (float 0))
        (.drawString g2d "on most systems. Helvetica is a very widely used sans serif font by Max Miedinger with input" (float 0) (float 18))
        (.drawString g2d "from Eduard Hoffmann.  The Regular weight, which this text should be set in, has a double" (float 0) (float 36))
        (.drawString g2d "story 'a' with a small curved terminal at the bottom of the main vertical stroke.  The 'g'" (float 0) (float 54))
        (.drawString g2d "is single story." (float 0) (float 72)))]
     [:graphics {:under false :translate [30 350]}
      (fn [g2d]
        (.setFont g2d (java.awt.Font. "Times New Roman" java.awt.Font/PLAIN 12))
        (.drawString g2d "All systems: This should have font size 12, and be Times New Roman." (float 0) (float 0))
        (.drawString g2d "Times New Roman is a popular classic serif font by Stanley Morison with input from Victor Lardent," (float 0) (float 18))
        (.drawString g2d "released in 1932.  The Regular weight, which this text should be set in, is a serif font that was" (float 0) (float 36))
        (.drawString g2d "designed for newsprint, optimized for readibility and compactness. It has both double story 'g' and 'a'." (float 0) (float 54)))]
     [:graphics {:under false :translate [30 440]}
      (fn [g2d]
        (.setFont g2d (java.awt.Font. "Palatino" java.awt.Font/PLAIN 12))
        (.drawString g2d "Windows: This should have font size 12, and be Palatino." (float 0) (float 0))
        (.drawString g2d "Palatino is a popular classic serif font by Hermann Zapf released in 1949.  The Regular weight, which" (float 0) (float 18))
        (.drawString g2d "this text should be set in, is a serif font that is more rounded than the default serif font, usually" (float 0) (float 36))
        (.drawString g2d "Times New Roman.  For completeness, it has both double story 'g' and 'a'." (float 0) (float 54)))]]
    "graphics-windows.pdf"))

  )
