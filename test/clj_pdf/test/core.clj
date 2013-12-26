(ns clj-pdf.test.core
  (:use clj-pdf.core clojure.test clojure.java.io)
  (:require [clojure.string :as re]))

(defn doc-to-str [doc]
  (let [out (new java.io.ByteArrayOutputStream)]
    (pdf doc out)
  (.toString out)))

(defn fix-pdf [pdf]
  (-> pdf
  (re/replace #"ModDate\((.*?)\)" "")
  (re/replace #"CreationDate\((.*?)\)" "")
  (re/replace #"\[(.*?)\]" "")))

(defn eq? [doc1 doc2]
  ;uncomment to generate test data
  ;(spit (str "test" java.io.File/separator doc2) (fix-pdf (doc-to-str doc1)))
  (is (= (fix-pdf (doc-to-str doc1)) (fix-pdf (slurp (str "test" java.io.File/separator doc2))))))

(deftest page-numbers
  (eq?
    [{:title  "Test doc"
      :left-margin   10
      :right-margin  50
      :top-margin    20
      :bottom-margin 25
      :pages true
      :font  {:size 11}
      :size          :a4
      :orientation   "landscape"
      :subject "Some subject"
      :author "John Doe"
      :creator "Jane Doe"
      :doc-header ["inspired by" "William Shakespeare"]
      :header "page header"
      :footer "page"}
     [:paragraph "I should have font size 11"]
     [:chunk "meta test"]]
    "pages1.pdf")

  (eq?
    [{:title  "Test doc"
      :left-margin   10
      :right-margin  50
      :top-margin    20
      :bottom-margin 25
      :pages true
      :font  {:size 11}
      :size          :a4
      :subject "Some subject"
      :author "John Doe"
      :creator "Jane Doe"
      :doc-header ["inspired by" "William Shakespeare"]
      :header "page header"
      :footer "page"
      :footer-separator " of "}
     [:paragraph "I should have font size 11"]
     [:chunk "meta test"]]
    "pages2.pdf"))

(deftest no-footer
  (eq?
   [{:title  "Test doc"
      :left-margin   10
      :right-margin  50
      :top-margin    20
      :bottom-margin 25
      :pages true
      :font  {:size 11}  
      :size          :a4
      :orientation   "landscape"
      :subject "Some subject"
      :author "John Doe"
      :creator "Jane Doe"
      :doc-header ["inspired by" "William Shakespeare"]
      :header "page header"
      :footer false}
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
      {:scale     12
       :align      :center
       :annotation ["FOO" "BAR"]
       :pad-left   100
       :pad-right  50}
      (javax.imageio.ImageIO/read (new java.io.File (str "test" java.io.File/separator "mandelbrot.jpg")))]]
    "image1.pdf")
  (eq?
    [{}
     [:image
      {:width     30
       :height     50
       :align      :center
       :annotation ["FOO" "BAR"]}
      (javax.imageio.ImageIO/read (new java.io.File (str "test" java.io.File/separator "mandelbrot.jpg")))]]
    "image2.pdf"))

(deftest doc-meta
  (eq?
    [{:title  "Test doc"
      :left-margin   10
      :right-margin  50
      :top-margin    20
      :bottom-margin 25
      :font  {:size 11}
      :size          :a4
      :orientation   "landscape"
      :subject "Some subject"
      :author "John Doe"
      :creator "Jane Doe"
      :doc-header ["inspired by" "William Shakespeare"]
      :header "page header"
      :footer "page"}
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

     [:table {:border false :header [{:color [100 100 100]} "Singe Header"]} ["foo" "bar" "baz"] ["foo1" "bar1" "baz1"] ["foo2" "bar2" "baz2"]]

     [:table {:cell-border false :header [{:color [100 100 100]} "Row 1" "Row 2" "Row 3"] :cellSpacing 20 :header-color [100 100 100]}
      ["foo"
       [:cell [:phrase {:style :italic :size 18 :family :halvetica :color [200 55 221]} "Hello Clojure!"]]
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
       [:phrase {:style :italic :size 18 :family :halvetica :color [0 255 221]} "Hello Clojure!"]
       [:phrase [:chunk {:style :strikethru} "chunk one"] [:chunk {:size 20} "Big text"] "some other text"]]
      "phrase.pdf"))

(deftest paragraph
  (eq? [{}
       [:paragraph "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Suspendisse convallis blandit justo non rutrum. In hac habitasse platea dictumst."]
       [:paragraph {:indent 50 :size 18} [:phrase {:style :bold :family :halvetica :color [0 255 221]} "Hello Clojure!"]]
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
       [:chart {:type :line-chart
                     :time-series true
                     :time-format "MM/yy"
                     :title "Time Chart"
                     :x-label "time"
                     :y-label "progress"}
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
       [:chapter "Chapter"
        [:section "Section Title"
         [:paragraph "Some content"]
         [:paragraph "Some more content"]
         [:section [:paragraph "Nested Section Title"]
          [:paragraph "nested section content"]]]]]
      "section.pdf"))

(deftest nil-element
  (eq? [{}
        nil]
       "nil.pdf"))
