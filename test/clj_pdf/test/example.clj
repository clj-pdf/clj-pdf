(ns clj-pdf.test.example
  (:use [clj-pdf.core])
  (:import [java.awt Color]))

(defn radians [degrees] (Math/toRadians degrees))

(defmacro rot [g2d angle & body]
  `(do (. ~g2d rotate (radians ~angle))
       (do ~@body)
       (. ~g2d rotate (radians (- 0 ~angle)))))

(defmacro trans [g2d dx dy & body]
  `(do (. ~g2d translate ~dx ~dy)
       (do ~@body)
       (. ~g2d translate (- 0 ~dx) (- 0 ~dy))))

(defn draw-tree [g2d length depth]
  (if (> depth 0)
    (do
      (.drawLine g2d 0 0 length 0)
      (trans g2d (int length) 0
             (rot g2d -30 (draw-tree g2d (* length 0.75) (- depth 1)))
             (rot g2d 30 (draw-tree g2d (* length 0.75) (- depth 1)))))))

#_(pdf
    [{:title         "Test doc"
      :header        "page header"
      :subject       "Some subject"
      :creator       "Jane Doe"
      :doc-header    ["inspired by" "William Shakespeare"]
      :right-margin  50
      :author        "John Doe"
      :bottom-margin 10
      :left-margin   10
      :top-margin    20
      :size          "a4"
      :footer        "page"}

     [:heading "Lorem Ipsum"]

     [:paragraph
      "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Donec non iaculis lectus. Integer vel libero libero. Phasellus metus augue, consequat a viverra vel, fermentum convallis sem. Etiam venenatis laoreet quam, et adipiscing mi lobortis sit amet. Fusce eu velit vitae dolor vulputate imperdiet. Suspendisse dui risus, mollis ut tempor sed, dapibus a leo. Aenean nisi augue, placerat a cursus eu, convallis viverra urna. Nunc iaculis pharetra pretium. Suspendisse sit amet erat nisl, quis lacinia dolor. Integer mollis placerat metus in adipiscing. Fusce tincidunt sapien in quam vehicula tincidunt. Integer id ligula ante, interdum sodales enim. Suspendisse quis erat ut augue porta laoreet."]

     [:paragraph
      "Sed pellentesque lacus vel sapien facilisis vehicula. Quisque non lectus lacus, at varius nibh. Integer porttitor porttitor gravida. Lorem ipsum dolor sit amet, consectetur adipiscing elit. Phasellus accumsan ante tincidunt magna dictum vulputate. Maecenas suscipit commodo leo sed mattis. Morbi dictum molestie justo eu egestas. Praesent lacus est, euismod vitae consequat non, accumsan in justo. Nam rhoncus dapibus nunc vel dignissim."]

     [:paragraph
      "Nulla id neque ac felis tempor pretium adipiscing ac tortor. Aenean ac metus sapien, at laoreet quam. Vivamus id dui eget neque mattis accumsan. Aliquam aliquam lacinia lorem ut dapibus. Fusce aliquam augue non libero viverra ut porta nisl mollis. Mauris in justo in nibh fermentum dapibus at ut erat. Maecenas vitae fermentum lectus. Nunc dolor nisl, commodo a pellentesque non, tincidunt id dolor. Nulla tellus neque, consectetur in scelerisque vitae, cursus vel urna. Phasellus ullamcorper ultrices nisi ac feugiat."]

     [:table {:header [{:background-color [100 100 100]} "FOO"] :cellSpacing 20}
      ["foo"
       [:cell
        [:phrase
         {:style "italic" :size 18 :family "helvetica" :color [200 55 221]}
         "Hello Clojure!"]]
       "baz"]
      ["foo1" [:cell {:background-color [100 10 200]} "bar1"] "baz1"]
      ["foo2" "bar2" [:cell ["table" ["Inner table Col1" "Inner table Col2" "Inner table Col3"]]]]]

     [:paragraph
      "Suspendisse consequat, mauris vel feugiat suscipit, turpis metus semper metus, et vulputate sem nisi a dolor. Duis egestas luctus elit eget dignissim. Vivamus elit elit, blandit id volutpat semper, luctus id eros. Duis scelerisque aliquam lorem, sed venenatis leo molestie ac. Vivamus diam arcu, sodales at molestie nec, pulvinar posuere est. Morbi a velit ante. Nulla odio leo, volutpat vitae eleifend nec, luctus ac risus. In hac habitasse platea dictumst. In posuere ultricies nulla, eu interdum erat rhoncus ac. Vivamus rutrum porta interdum. Nulla pulvinar dui quis velit varius tristique dignissim sem luctus. Aliquam ac velit enim. Sed sed nisi faucibus ipsum congue lacinia. Morbi id mi in lectus vehicula dictum vel sed metus. Sed commodo lorem non nisl vulputate elementum. Fusce nibh dui, auctor a rhoncus eu, rhoncus eu eros."]

     [:paragraph
      "Nulla pretium ornare nisi at pulvinar. Praesent lorem diam, pulvinar nec scelerisque et, mattis vitae felis. Integer eu justo sem, non molestie nisl. Aenean interdum erat non nulla commodo pretium. Quisque egestas ullamcorper lacus id interdum. Ut scelerisque, odio ac mollis suscipit, libero turpis tempus nulla, placerat pretium tellus purus eu nunc. Donec nec nisi non sem vehicula posuere et eget sem. Aliquam pretium est eget lorem lacinia in commodo nisl laoreet. Curabitur porttitor dignissim eros, nec semper neque tempor non. Duis elit neque, sagittis vestibulum consequat ut, rhoncus sed dui."]

     [:anchor {:style {:size 15} :leading 20} "some anchor"]

     [:anchor [:phrase {:style "bold"} "some anchor phrase"]]

     [:anchor "plain anchor"]

     [:chunk {:style "bold"} "small chunk of text"]

     [:phrase "some text here"]

     [:phrase {:style "italic" :size 18 :family "helvetica" :color [0 255 221]} "Hello Clojure!"]

     [:chapter [:paragraph "Second Chapter"]]

     [:paragraph {:keep-together true :indent 20} "a fine paragraph"]

     [:list {:roman true} [:chunk {:style "bold"} "a bold item"] "another item" "yet another item"]

     [:chapter "Charts"]

     [:chart
      {:type :bar-chart :title "Bar Chart" :x-label "Items" :y-label "Quality"}
      [2 "Foo"]
      [4 "Bar"]
      [10 "Baz"]]

     [:chart
      {:type :line-chart :title "Line Chart" :x-label "checkpoints" :y-label "units"}
      ["Foo" [1 10] [2 13] [3 120] [4 455] [5 300] [6 600]]
      ["Bar" [1 13] [2 33] [3 320] [4 155] [5 200] [6 300]]]

     [:chart {:type :pie-chart :title "Big Pie"} ["One" 21] ["Two" 23] ["Three" 345]]

     [:chart
      {:type        :line-chart
       :time-series true
       :title       "Time Chart"
       :x-label     "time"
       :y-label     "progress"}
      ["Incidents"
       ["2011-01-03-11:20:11" 200]
       ["2011-01-03-11:25:11" 400]
       ["2011-01-03-11:35:11" 350]
       ["2011-01-03-12:20:11" 600]]]

     [:chapter "Graphics2D"]

     [:paragraph
      "Tree Attribution: "
      [:anchor
       {:style  {:color [0 0 200]}
        :target "http://www.curiousattemptbunny.com/2009/01/simple-clojure-graphics-api.html"}
       "http://www.curiousattemptbunny.com/2009/01/simple-clojure-graphics-api.html"]]

     [:graphics {:under false :translate [53 120]}
      (fn [g2d]
        (doto g2d
          (.setColor Color/BLACK)
          (.setFont  (java.awt.Font. "SansSerif" java.awt.Font/BOLD 20))
          (.drawString ":graphics Drawing" (float 0) (float 0))))]

     [:graphics {:translate [150 300] :rotate (radians -90)}
      (fn [g2d]
        (.setColor g2d Color/GREEN)
        (draw-tree g2d 50 10))]

     [:graphics {:under false :translate [70 270] :rotate (radians -35)}
      (fn [g2d]
        (doto g2d
          (.setColor (java.awt.Color. 96 96 96))
          (.setFont  (java.awt.Font. "Serif" java.awt.Font/PLAIN 14))
          (.drawString "drawString with setFont and rotate" (float 0) (float 0))))]
     
     [:chart {:type      :pie-chart
              :title     "Vector Pie"
              :vector    true
              :width     300 :height 250
              :translate [270 100]}
      ["One" 21] ["Two" 23] ["Three" 345]]

     [:chart
      {:type      :line-chart
       :title     "Vector Line Chart"
       :x-label   "checkpoints"
       :y-label   "units"
       :vector    true
       :width     500 :height 300
       :translate [50 400]}
      ["Foo" [1 10] [2 13] [3 120] [4 455] [5 300] [6 600]]
      ["Bar" [1 13] [2 33] [3 320] [4 155] [5 200] [6 300]]]

     [:chapter "Embedded SVG"]

     [:paragraph
      "Attribution: "
      [:anchor
       {:style  {:color [0 0 200]}
        :target "https://en.wikipedia.org/wiki/File:Example.svg"}
       "https://en.wikipedia.org/wiki/File:Example.svg"]]

     [:svg {:under true :translate [0 200] :scale 0.95}
      (clojure.java.io/file "test/Example.svg")]

     [:pagebreak]

     [:paragraph
      "Attribution: "
      [:anchor
       {:style  {:color [0 0 200]}
        :target "https://commons.wikimedia.org/wiki/SVG_examples"}
       "https://commons.wikimedia.org/wiki/SVG_examples"]]

     [:svg {}
      "<?xml version=\"1.0\"?>
       <!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\" \"http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd\">
       <svg xmlns=\"http://www.w3.org/2000/svg\" width=\"467\" height=\"462\">
         <rect x=\"80\" y=\"60\" width=\"250\" height=\"250\" rx=\"20\" style=\"fill:#ff0000; stroke:#000000;stroke-width:2px;\" />
         <rect x=\"140\" y=\"120\" width=\"250\" height=\"250\" rx=\"40\" style=\"fill:#0000ff; stroke:#000000; stroke-width:2px; fill-opacity:0.7;\" />
       </svg>"]

     [:svg {:translate [100 450]}
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
       <!DOCTYPE svg>
       <svg xmlns=\"http://www.w3.org/2000/svg\" width=\"304\" height=\"290\">
          <path d=\"M2,111 h300 l-242.7,176.3 92.7,-285.3 92.7,285.3 z\"
                style=\"fill:#FB2;stroke:#F00;stroke-width:3;stroke-linejoin:round\"/>
       </svg>"]]

    "example.pdf")

#_(pdf
    [{:title      "Test doc"
      :top-margin 20
      :letterhead ["A letterhead"]
      :header     {:table
                   [:pdf-table
                    {:align            :left
                     :border           false
                     :background-color [200 200 200]}
                    [20 15 60]
                    ["This is a table header" "second column" "third column"]]}
      :subject    "Some subject"
      :creator    "Jane Doe"
      :doc-header ["inspired by" "William Shakespeare"]
      :author     "John Doe"
      :size       "a4"
      :pages      true
      :footer     "a nice footer"}
     [:paragraph "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Suspendisse tincidunt, neque id egestas vestibulum, turpis nisl egestas dolor, vel efficitur risus felis non urna. Aliquam a nunc scelerisque, imperdiet justo eget, congue nulla. Duis finibus metus in mauris cursus placerat. In hac habitasse platea dictumst. Duis semper enim enim, et vestibulum massa placerat dapibus. Suspendisse nec imperdiet elit. Aenean tincidunt erat at neque facilisis, id vestibulum tellus molestie."]
     [:pagebreak]
     [:paragraph "Nullam vitae leo pulvinar purus pulvinar egestas in a turpis. Mauris pharetra sodales odio, sed aliquet dui aliquet eu. Nulla a ipsum non lectus dapibus aliquam. Nulla imperdiet ante at felis lobortis, sed egestas lacus aliquet. Fusce tempor diam in libero molestie, quis mollis lectus venenatis. In ut fermentum tortor. Nam ornare eros sed risus viverra, eget fringilla urna facilisis."]
     [:pagebreak]
     [:paragraph "In bibendum neque a sollicitudin imperdiet. Aenean justo nisi, congue at augue ac, pharetra feugiat erat. Donec metus sapien, blandit ut facilisis non, egestas at orci. Proin pellentesque ipsum non libero dignissim, sed porta diam hendrerit. Fusce a est condimentum, cursus turpis eu, semper nulla. Vestibulum a finibus eros. Aenean eget dapibus nulla. Morbi viverra nisi gravida, venenatis tellus hendrerit, tincidunt mi. Integer molestie nisl in pellentesque maximus. Praesent porta blandit mauris id bibendum. Maecenas viverra arcu a feugiat pharetra. In condimentum massa arcu. Proin consectetur, nisi id congue semper, nisi odio gravida libero, a laoreet magna turpis at ante."]]
    "footer-header1.pdf")

#_(collate (java.io.FileOutputStream. (clojure.java.io/file "merged.pdf"))
           "footer-header1.pdf" "footer-header.pdf")

#_(pdf
    [{:size   :a4
      :font
              {:style  :bold
               :size   15
               :family :helvetica
               :color  [0 234 123]}
      :header {:x 20
               :y 50
               :table
                  [:pdf-table
                   {:border false
                    :style  :bold
                    :size   10
                    :family :helvetica
                    :color  [0 234 0]}
                   [20 15 60]
                   ["This is a table header" "second column" "third column"]]}
      :footer {:table
               [:pdf-table
                {:border false}
                [20 15 60]
                ["This is a table footer" "second column" "third column"]]}}
     [:paragraph "hi"]]
    "test.pdf")



#_(defn watermark [text input-image output-image]
  (let [img  (javax.imageio.ImageIO/read (java.io.File. input-image))
        g2d  (doto (.getGraphics img)
               (.setComposite (java.awt.AlphaComposite/getInstance java.awt.AlphaComposite/SRC_OVER (float 0.1)))
               (.setColor java.awt.Color/BLUE)
               (.setFont (java.awt.Font. "Arial" java.awt.Font/BOLD 64)))
        rect (.. g2d (getFontMetrics) (getStringBounds text g2d))]
    (.drawString g2d
                 text
                 (int
                   (- (.getWidth img)
                      (/ (.getWidth rect) 2)))
                 (int (/ (.getHeight img) 2)))
    (javax.imageio.ImageIO/write img "jpg" (java.io.File. output-image))
    (.dispose g2d)))

#_(watermark "WATERMARK" "example.png" "watermarked.jpg")
