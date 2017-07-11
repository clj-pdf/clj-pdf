(ns clj-pdf.test.core
  (:use clj-pdf.core clojure.test clojure.java.io)
  (:require [clojure.string :as re]))

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

(deftest page-numbers
  (eq?
    [{:title         "Test doc"
      :left-margin   10
      :right-margin  50
      :top-margin    20
      :bottom-margin 25
      :pages         true
      :font          {:size 11}
      :size          :a4
      :orientation   "landscape"
      :subject       "Some subject"
      :author        "John Doe"
      :creator       "Jane Doe"
      :doc-header    ["inspired by" "William Shakespeare"]
      :header        "page header"
      :footer        "page"}
     [:paragraph "I should have font size 11"]
     [:chunk "meta test tttt"]]
    "pages1.pdf")

  (eq?
    [{}
     [:image {:width 50 :height 50 :base64 true} (slurp "test/b64logo")]]
    "base64image.pdf")

  (eq?
    [{:title         "Test doc"
      :left-margin   10
      :right-margin  50
      :top-margin    20
      :bottom-margin 25
      :pages         true
      :font          {:size 11}
      :size          :a4
      :subject       "Some subject"
      :author        "John Doe"
      :creator       "Jane Doe"
      :doc-header    ["inspired by" "William Shakespeare"]
      :header        "page header"
      :footer        {:text             "page"
                      :footer-separator " of "}}
     [:paragraph "I should have font size 11"]
     [:chunk "meta test"]]
    "pages2.pdf"))

(deftest no-footer
  (eq?
    [{:title         "Test doc"
      :left-margin   10
      :right-margin  50
      :top-margin    20
      :bottom-margin 25
      :pages         true
      :font          {:size 11}
      :size          :a4
      :orientation   "landscape"
      :subject       "Some subject"
      :author        "John Doe"
      :creator       "Jane Doe"
      :doc-header    ["inspired by" "William Shakespeare"]
      :header        "page header"
      :footer        false}
     [:paragraph "I should have font size 11"]
     [:chunk "meta test"]]
    "nofooter.pdf"))

(deftest image
  (eq?
    [{}
     [:image
      {:xscale     0.5
       :yscale     0.8
       :align      :center
       :annotation ["FOO" "BAR"]
       :pad-left   100
       :pad-right  50}
      (javax.imageio.ImageIO/read (new java.io.File (str "test" java.io.File/separator "mandelbrot.jpg")))]]
    "image.pdf")
  (eq?
    [{}
     [:image
      {:scale      12
       :align      :center
       :annotation ["FOO" "BAR"]
       :pad-left   100
       :pad-right  50}
      (javax.imageio.ImageIO/read (new java.io.File (str "test" java.io.File/separator "mandelbrot.jpg")))]]
    "image1.pdf")
  (eq?
    [{}
     [:image
      {:width      30
       :height     50
       :align      :center
       :annotation ["FOO" "BAR"]}
      (javax.imageio.ImageIO/read (new java.io.File (str "test" java.io.File/separator "mandelbrot.jpg")))]]
    "image2.pdf"))

(deftest doc-meta
  (eq?
    [{:title         "Test doc"
      :left-margin   10
      :right-margin  50
      :top-margin    20
      :bottom-margin 25
      :font          {:size 11}
      :size          :a4
      :orientation   "landscape"
      :subject       "Some subject"
      :author        "John Doe"
      :creator       "Jane Doe"
      :doc-header    ["inspired by" "William Shakespeare"]
      :header        "page header"
      :footer        "page"}
     [:paragraph "I should have font size 11"]
     [:chunk "meta test"]]
    "header.pdf"))

(deftest table
  (eq?
    [{}
     [:table {:header ["Row 1" "Row 2" "Row 3"] :width 50 :border false :cell-border false}
      [[:cell {:colspan 2} "Foo"] "Bar"]
      ["foo1" "bar1" "baz1"]
      ["foo2" "bar2" "baz2"]]

     [:table {:border-width 10 :header ["Row 1" "Row 2" "Row 3"]} ["foo" "bar" "baz"] ["foo1" "bar1" "baz1"] ["foo2" "bar2" "baz2"]]

     [:table {:border false :header [{:color [100 100 100]} "Single Header"]} ["foo" "bar" "baz"] ["foo1" "bar1" "baz1"] ["foo2" "bar2" "baz2"]]

     [:table {:cell-border false :header [{:background-color [100 100 255]} "Row 1" "Row 2" "Row 3"] :spacing 20 :header-color [100 100 100]}
      ["foo"
       [:cell [:phrase {:style :italic :size 18 :family :helvetica :color [200 55 221]} "Hello Clojure!"]]
       "baz"]
      ["foo1" [:cell {:color [100 10 200]} "bar1"] "baz1"]
      ["foo2" "bar2" "baz2"]]]
    "table.pdf"))

(deftest line
  (eq? [{} [:line]]
       "line.pdf"))

(deftest chapter
  (eq? [{} [:chapter "Chapter title"]]
       "chapter.pdf"))

(deftest anchor
  (eq? [{}
        [:anchor {:style {:size 15} :leading 20} "some anchor"]
        [:anchor [:phrase {:style :bold} "some anchor phrase"]]
        [:anchor "plain anchor"]
        [:anchor {:target "http://google.com"} "google"]
        [:anchor {:id "target"} "some anchor"]
        [:anchor {:target "#target"} "another anchor"]]
       "anchor.pdf"))

(deftest chunk-test
  (eq? [{} [:chunk {:style :bold} "small chunk of text"]]
       "chunk.pdf"))


(deftest phrase
  (eq? [{}
        [:phrase "some text here"]
        [:phrase {:style :italic :size 18 :family :helvetica :color [0 255 221]} "Hello Clojure!"]
        [:phrase [:chunk {:style :strikethru} "chunk one"] [:chunk {:size 20} "Big text"] "some other text"]]
       "phrase.pdf"))

(deftest paragraph
  (eq? [{}
        [:paragraph "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Suspendisse convallis blandit justo non rutrum. In hac habitasse platea dictumst."]
        [:paragraph {:indent 50 :size 18} [:phrase {:style :bold :family :helvetica :color [0 255 221]} "Hello Clojure!"]]
        [:paragraph {:keep-together true :indent 20} "a fine paragraph"]
        [:paragraph {:align :center} "centered paragraph"]
        [:paragraph "256" [:chunk {:super true} "5"] " and 128" [:chunk {:sub true} "2"]]]

       "paragraph.pdf"))

(deftest list-test
  (eq? [{} [:list {:roman true} [:chunk {:style :bold} "a bold item"] "another item" "yet another item"]]
       "list.pdf"))

#_(deftest chart
    (eq? [{}
          [:chart {:type :bar-chart :title "Bar Chart" :x-label "Items" :y-label "Quality"} [2 "Foo"] [4 "Bar"] [10 "Baz"]]
          [:chart {:type :line-chart :title "Line Chart" :x-label "checkpoints" :y-label "units"}
           ["Foo" [1 10] [2 13] [3 120] [4 455] [5 300] [6 600]]
           ["Bar" [1 13] [2 33] [3 320] [4 155] [5 200] [6 300]]]
          [:chart {:type :pie-chart :title "Big Pie"} ["One" 21] ["Two" 23] ["Three" 345]]
          [:chart {:type :line-chart :time-series true :title "Time Chart" :x-label "time" :y-label "progress"}
           ["Incidents"
            ["2011-01-03-11:20:11" 200]
            ["2011-01-03-11:25:11" 400]
            ["2011-01-03-11:35:11" 350]
            ["2011-01-03-12:20:11" 600]]]
          [:chart {:type        :line-chart
                   :time-series true
                   :time-format "MM/yy"
                   :title       "Time Chart"
                   :x-label     "time"
                   :y-label     "progress"}
           ["Occurances" ["01/11" 200] ["02/12" 400] ["05/12" 350] ["11/13" 600]]]]
         "charts.pdf"))

(deftest heading
  (eq? [{} [:heading "Lorem Ipsum"]
        [:heading {:size 15} "Lorem Ipsum"]
        [:heading {:align :center} "Centered"]]
       "heading.pdf"))


(deftest sub-super
  (eq? [{}
        [:subscript "some subscript text"]
        [:subscript {:style :bold} "some bold subscript text"]
        [:superscript "some superscript text"]
        [:superscript {:style :bold} "some bold superscript text"]]
       "subsuper.pdf"))

(deftest spacer
  (eq? [{}
        [:paragraph "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Suspendisse convallis blandit justo non rutrum. In hac habitasse platea dictumst."]
        [:spacer 5]
        [:paragraph "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Suspendisse convallis blandit justo non rutrum. In hac habitasse platea dictumst."]]
       "spacer.pdf"))

(deftest section
  (eq? [{}
        [:chapter "Chapter 1"
         [:section "Section Title 1"
          [:paragraph "Some content 1"]
          [:paragraph "Some more content 1"]
          [:section [:paragraph "Nested Section Title 1"]
           [:paragraph "nested section content 1"]]]]
        [:chapter "Chapter 2"
         [:section "Section Title 2"
          [:paragraph "Some content 2"]
          [:paragraph "Some more content 2"]
          [:section [:paragraph "Nested Section Title 2"]
           [:paragraph "nested section content 2"]]]]]
       "section.pdf"))

(deftest nil-element
  (eq? [{}
        nil]
       "nil.pdf"))
