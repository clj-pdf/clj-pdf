(ns clj-pdf.core
  (:import [com.lowagie.text Chapter Document Font Header List ListItem Paragraph Phrase]
           java.awt.Color
           com.lowagie.text.pdf.PdfWriter
           java.io.FileOutputStream))

(declare make-section)

(defn font [family size style color]
  (new Font
       (condp = family
         :courier   (Font/COURIER)
         :helvetica (Font/HELVETICA)
         :times-roman (Font/TIMES_ROMAN)
         :symbol      (Font/SYMBOL)
         :zapfdingbats (Font/ZAPFDINGBATS))
       (float size)
       (condp = style
         :bold (Font/BOLD)
         :bold-italic (Font/BOLDITALIC)
         :normal (Font/NORMAL)
         :strikethru (Font/STRIKETHRU)
         :underline (Font/UNDERLINE))
       (condp = color
         :black (new Color 0 0 0))))

(defn- header [[name text]]
  (new Header name text))

(defn- chapter [[number & [title]]]  
  (if title 
    (new Chapter (if (string? title) title  (make-section title)) number)
    (new Chapter number)))

(defn- paragraph [text]
  (new Paragraph text))

(defn- li [[{numbered :numbered 
               lettered :lettered} 
              & items]]
  (let [list (new List (or numbered false) (or lettered false))]
    (doseq [item items]
      (.add list (new ListItem (if (string? item) item (make-section item)))))
    list))

(defn- phrase [[{style  :style
                 size   :size
                 color  :color
                 family :family} text]]  
  (new Phrase text (font family size style color)))

(defn- make-section [[k v]]
  (condp = k
      :chapter   (chapter v)      
      :header    (header v)
      :list      (li v)
      :paragraph (paragraph v)
      :phrase    (phrase v)))

(defn write-doc [content out]
  (let [doc (new Document)] 
    (PdfWriter/getInstance 
      doc 
      (if (string? out) (new FileOutputStream out) out))
    (.open doc)
    (doseq [item (partition 2 content)] 
      (.add doc (make-section item)))
    (.close doc)))

(write-doc [:header ["inspired by" "William Shakespeare"]
            :chapter [1 "First Chapter"]
            :paragraph "Hello Clojure!"
            :chapter [2]
            :paragraph "Some more stuff happened"
            :chapter [3 [:paragraph "Third Chapter"]]
            :list [{:numbered true :lettered true} "foo" "bar" "baz" [:phrase [{:style  :bold
                                                                                :size   18
                                                                                :family :helvetica
                                                                                :color  :black} "Bold text"]]]
            
            ] 
           "test1.pdf")
