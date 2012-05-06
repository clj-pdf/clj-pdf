(ns clj-pdf.core
  (:use [clojure.set :only (rename-keys)])
  (:require [clj_pdf.charting :as charting])
  (:import
    java.awt.Color
    com.lowagie.text.pdf.draw.LineSeparator  
    [com.lowagie.text
     Anchor
     Annotation
     Cell
     ChapterAutoNumber
     Chunk
     Document
     Font
     GreekList
     HeaderFooter
     Image
     List
     ListItem
     PageSize
     Paragraph
     Phrase
     Rectangle
     RomanList
     Table
     ZapfDingbatsList
     ZapfDingbatsNumberList]
    [com.lowagie.text.pdf BaseFont PdfContentByte PdfReader PdfStamper PdfWriter]
    [java.io PushbackReader InputStreamReader FileOutputStream ByteArrayOutputStream]))

(declare make-section)

(defn get-alignment [align]
  (condp = align "left" 0, "center" 1, "right" 2, 0))

(defn font
  [{style   :style
    size    :size
    [r g b] :color
    family  :family}]
  (new Font
       (condp = family
         "courier"      (Font/COURIER)
         "helvetica"    (Font/HELVETICA)
         "times-roman"  (Font/TIMES_ROMAN)
         "symbol"       (Font/SYMBOL)
         "zapfdingbats" (Font/ZAPFDINGBATS)
         (Font/HELVETICA))
       (float (if size size 11))
       (condp = style
         "bold"        (Font/BOLD)
         "italic"      (Font/ITALIC)
         "bold-italic" (Font/BOLDITALIC)
         "normal"      (Font/NORMAL)
         "strikethru"  (Font/STRIKETHRU)
         "underline"   (Font/UNDERLINE)
         (Font/NORMAL))
       (if (and r g b)
         (new Color r g b)
         (new Color 0 0 0))))


(defn- page-size [size]
  (condp = size
    "a0"                        (PageSize/A0)
    "a1"                        (PageSize/A1)
    "a2"                        (PageSize/A2)
    "a3"                        (PageSize/A3)
    "a4"                        (PageSize/A4)
    "a5"                        (PageSize/A5)
    "a6"                        (PageSize/A6)
    "a7"                        (PageSize/A7)
    "a8"                        (PageSize/A8)
    "a9"                        (PageSize/A9)
    "a10"                       (PageSize/A10)
    "arch-a"                    (PageSize/ARCH_A)
    "arch-b"                    (PageSize/ARCH_B)
    "arch-c"                    (PageSize/ARCH_C)
    "arch-d"                    (PageSize/ARCH_D)
    "arch-e"                    (PageSize/ARCH_E)
    "b0"                        (PageSize/B0)
    "b1"                        (PageSize/B1)
    "b2"                        (PageSize/B2)
    "b3"                        (PageSize/B3)
    "b4"                        (PageSize/B4)
    "b5"                        (PageSize/B5)
    "b6"                        (PageSize/B6)
    "b7"                        (PageSize/B7)
    "b8"                        (PageSize/B8)
    "b9"                        (PageSize/B9)
    "b10"                       (PageSize/B10)
    "crown-octavo"              (PageSize/CROWN_OCTAVO)
    "crown-quarto"              (PageSize/CROWN_QUARTO)
    "demy-octavo"               (PageSize/DEMY_OCTAVO)
    "demy-quarto"               (PageSize/DEMY_QUARTO)
    "executive"                 (PageSize/EXECUTIVE)
    "flsa"                      (PageSize/FLSA)
    "flse"                      (PageSize/FLSE)
    "halfletter"                (PageSize/HALFLETTER)
    "id-1"                      (PageSize/ID_1)
    "id-2"                      (PageSize/ID_2)
    "id-3"                      (PageSize/ID_3)
    "large-crown-octavo"        (PageSize/LARGE_CROWN_OCTAVO)
    "large-crown-quarto"        (PageSize/LARGE_CROWN_QUARTO)
    "ledger"                    (PageSize/LEDGER)
    "legal"                     (PageSize/LEGAL)
    "letter"                    (PageSize/LETTER)
    "note"                      (PageSize/NOTE)
    "penguin-large-paperback"   (PageSize/PENGUIN_LARGE_PAPERBACK)
    "penguin-small-paperback"   (PageSize/PENGUIN_SMALL_PAPERBACK)
    "postcard"                  (PageSize/POSTCARD)
    "royal-octavo"              (PageSize/ROYAL_OCTAVO)
    "royal-quarto"              (PageSize/ROYAL_QUARTO)
    "small-paperback"           (PageSize/SMALL_PAPERBACK)
    "tabloid"                   (PageSize/TABLOID)
    (PageSize/A4)))
 
 
(defn- page-orientation [page-size orientation]
  (if page-size
    (condp = orientation
      "landscape"    (.rotate page-size)
      page-size)))

 
(defn- chapter [_ title] (new ChapterAutoNumber (make-section title)))


(defn- heading [meta & content]
  (let [style (if (contains? meta :heading-style)
                (rename-keys meta {:heading-style :style})
                {:style {:size 18 :style "bold"}})
        align (if (contains? meta :align)
                [:align (:align meta)]
                [:align "left"])
        attrs (conj style align)]
    (make-section (into [:paragraph attrs] content))))


(defn- paragraph [{indent        :indent
                   style         :style
                   keep-together :keep-together
                   leading       :leading
                   align         :align} content]
  (let [paragraph (if style
                    (new Paragraph (make-section content) (font style))
                    (new Paragraph (make-section content)))]
    (if keep-together (.setKeepTogether paragraph true))
    (if indent (.setFirstLineIndent paragraph (float indent)))
    (if leading (.setLeading paragraph (float leading)))
    (if align (.setAlignment paragraph (get-alignment align)))
    paragraph ))


(defn- li [{numbered                :numbered
            lettered                :lettered
            roman                   :roman
            greek                   :greek
            dingbats                :dingbats
            dingbats-char-num       :dingbats-char-num
            dingbatsnumber          :dingbatsnumber
            dingbatsnumber-type     :dingbatsnumber-type}
           & items]
  (let [list (cond
               roman           (new RomanList)
               greek           (new GreekList)
               dingbats        (new ZapfDingbatsList dingbats-char-num)
               dingbatsnumber  (new ZapfDingbatsNumberList dingbatsnumber-type)
               :else (new List (or numbered false) (or lettered false)))]
    (doseq [item items]
      (.add list (new ListItem (make-section item))))
    list))


(defn- phrase
  [meta & content]
  (let [leading (:leading meta)
        p (doto (new Phrase)   
            (.setFont (font meta))
            (.addAll (map make-section content)))] 
    (if leading (.setLeading p (float leading))) p))
 

(defn- text-chunk [font-style content]
  (new Chunk (make-section content) (font font-style)))


(defn- annotation
  ([_ title text] (annotation title text))
  ([title text] (new Annotation title text)))


(defn- anchor [{style   :style
                leading :leading}
               content]
  (cond (and style leading) (new Anchor (float leading) content (font style))
        leading             (new Anchor (float leading) (make-section content))
        style               (new Anchor content (font style))
        :else               (new Anchor (make-section content))))


(defn- cell [element]
  (cond
    (string? element)
    element
    (= :cell (first element))
    (let [meta? (map? (second element))
          content (last element)
          c (if (string? content) (new Cell content) (new Cell))]
      
      (if meta?
        (let [{[r g b] :color
               colspan :colspan
               rowspan :rowspan
               border  :border
               align   :align} (second element)]
          
          (if (and r g b) (.setBackgroundColor c (new Color (int r) (int g) (int b))))
          (when (not (nil? border))
            (.setBorder c (if border Rectangle/BOX Rectangle/NO_BORDER)))
          (if rowspan (.setRowspan c (int rowspan)))
          (if colspan (.setColspan c (int colspan)))
          (.setHorizontalAlignment c (get-alignment align))))
      (if (string? content) c (doto c (.addElement (make-section content)))))
 
    :else
    (doto (new Cell) (.addElement (make-section element)))))
 
 
(defn- table-header [tbl header cols]
  (when header
    (let [meta? (map? (first header))
          header-data (if meta? (rest header) header)
          set-bg #(if-let [[r g b] (if meta? (:color (first header)))]
                    (doto % (.setBackgroundColor (new Color (int r) (int g) (int b)))) %)]
      (if (= 1 (count header-data))
        (let [header-text (make-section [:chunk {:style "bold"} (first header-data)])
              header-cell (doto (new Cell header-text)
                            (.setHorizontalAlignment 1)
                            (.setHeader true)
                            (.setColspan cols))]
          (set-bg header-cell)
          (.addCell tbl header-cell))
       
        (doseq [h header-data]
          (let [header-text (make-section [:chunk {:style "bold"} h])
                header-cell (doto (new Cell header-text) (.setHeader true))]
            (set-bg header-cell)
            (.addCell tbl header-cell)))))
    (.endHeaders tbl)))
 
 
(defn- table [{[r g b]      :color
               spacing      :spacing
               padding      :padding
               offset       :offset
               header       :header
               border       :border
               border-width :border-width
               cell-border  :cell-border
               width        :width
               align        :align
               num-cols     :num-cols} & rows]
  (when (< (count rows) 1) (throw (new Exception "Table must contain rows!")))
  
  (let [cols (or num-cols (apply max (map count rows)))
        tbl   (doto (new Table cols (count rows)) (.setWidth (float (or width 100))))]

    (if (= false border)
      (.setBorder tbl Rectangle/NO_BORDER)
      (when border-width (.setBorderWidth tbl (float border-width))))
 
    (when (= false cell-border)
      (.setDefaultCell tbl (doto (new Cell) (.setBorder Rectangle/NO_BORDER))))
   
    (if (and r g b) (.setBackgroundColor tbl (new Color (int r) (int g) (int b))))
    (.setPadding tbl (if padding (float padding) (float 3)))
    (if spacing (.setSpacing tbl (float spacing)))
    (if offset (.setOffset tbl (float offset)))
    (table-header tbl header cols)
 
    (.setAlignment tbl (get-alignment align))
   
    (doseq [row rows]
      (doseq [column row]
        (.addCell tbl (cell column))))
    tbl))


(defn- image [{xscale        :xscale
               yscale        :yscale
               align         :align
               [title text]  :annotation
               pad-left      :pad-left
               pad-right     :pad-right                                            
               left-margin   :left-margin
               right-margin  :right-margin
               top-margin    :top-margin
               bottom-margin :bottom-margin
               page-width    :page-width
               page-height   :page-height} 
              img-data]
  (let [img (cond 
              (instance? java.awt.Image img-data)
              (Image/getInstance (.createImage (java.awt.Toolkit/getDefaultToolkit) (.getSource img-data)) nil)              
              (or (string? img-data) (instance? java.net.URL img-data)) 
              (Image/getInstance img-data)              
              :else 
              (throw (new Exception (str "Unsupported image data: " img-data ", must be one of java.net.URL, java.awt.Image, or filename string"))))
        width (.getWidth img)
        height (.getHeight img)
        available-width (- page-width (+ left-margin right-margin))
        available-height (- page-height (+ top-margin bottom-margin))
        page-scale (* 100 (if (> width height) 
                            (/ available-width width) 
                            (/ available-height height)))] 
        
    (when align (.setAlignment img (get-alignment align)))
    (when (and title text) (.setAnnotation img (make-section [:annotation title text])))
    (when pad-left (.setIndentationLeft img (float pad-left)))
    (when pad-right (.setIndentationRight img (float pad-right)))    
            
    ;;scale relative to page size
    (cond
      (and xscale yscale) (.scalePercent img (float (* page-scale xscale)) (float (* page-scale yscale)))
      xscale (.scalePercent img (float (* page-scale xscale)) (float 100))
      yscale (.scalePercent img (float 100) (float (* page-scale yscale)))
      :else (when (or (>  width available-width) (>  height available-height))
              (.scalePercent img (float page-scale))))
    
    img))


(defn- chart [& params]  
  (let [width (:page-width (first params))
        height (:page-height (first params))]
    (doto (Image/getInstance (apply charting/chart params) nil)
      (.scaleToFit  (float (* width 0.8)) (float (* height 0.8)))
      (.setDpi 300 300))))
 
(defn- line [& args]
  (doto (new LineSeparator) (.setOffset -5)))
 

(defn- spacer [_ height]
  (make-section [:paragraph (apply str (take height (repeat "\n")))]))


(defn- make-section
  ([element] (if element (make-section {} element) ""))
  ([meta element]
    (if (string? element)
      element
      (let [[element-name & content] element
            tag (if (string? element-name) (keyword element-name) element-name)
            params? (map? (first content))
            params (if  params? (merge meta (first content)) meta)
            elements (if params? (rest content) content)]
       
        (apply
          (condp = tag
            :anchor     anchor
            :annotation annotation
            :cell       cell
            :chapter    chapter
            :chart      chart
            :chunk      text-chunk
            :heading    heading
            :image      image
            :line       line
            :list       li
            :paragraph  paragraph
            :phrase     phrase
            :spacer     spacer
            :table      table)
          (cons params elements))))))
 
 (defn- append-to-doc [font-style width height item doc]
  (if-let [section (make-section {:style font-style 
                                  :left-margin (.leftMargin doc)
                                  :right-margin (.rightMargin doc)
                                  :top-margin (.topMargin doc)
                                  :bottom-margin (.bottomMargin doc)
                                  :page-width width 
                                  :page-height height} item)]
    (.add doc section)))
 
 (defn- add-header [header doc]
   (if header
          (.setHeader doc
            (doto (new HeaderFooter (new Phrase header) false) (.setBorderWidthTop 0)))))
 
(defn- setup-doc [{left-margin          :left-margin
                  right-margin          :right-margin
                  top-margin            :top-margin
                  bottom-margin         :bottom-margin
                  title                 :title                  
                  subject               :subject
                  [nom head]            :doc-header
                  header                :header
                  letterhead            :letterhead
                  footer                :footer
                  total-pages?          :pages
                  author                :author
                  creator               :creator
                  size                  :size
                  font-style            :font
                  orientation           :orientation}
                  out]
 
  (let [doc    (new Document (page-orientation (page-size size) orientation))
        width  (.. doc getPageSize getWidth)
        height (.. doc getPageSize getHeight)
        output-stream (if (string? out) (new FileOutputStream out) out)
        temp-stream   (if total-pages? (new ByteArrayOutputStream))]
 
    ;;header and footer must be set before the doc is opened, or itext will not put them on the first page!
    ;;if we have to print total pages, then the document has to be post processed
    (if total-pages?
      (PdfWriter/getInstance doc temp-stream)
      (do
        (PdfWriter/getInstance doc output-stream)       
        (if footer
          (.setFooter doc
            (doto (new HeaderFooter (new Phrase (str footer " ") (font {:size 10})), true)
              (.setBorder 0)
              (.setAlignment 2))))))
       
    ;;if we have a letterhead then we want to put it on the first page instead of the header, 
    ;;so we will open doc beofore adding the header
    (if  letterhead 
      (do
        (.open doc)
        (append-to-doc (or font-style {}) width height letterhead doc)
        (add-header header doc))
      (do
        (add-header header doc)
        (.open doc)))
   
    (if (and left-margin right-margin top-margin bottom-margin)
    (.setMargins doc
      (float left-margin)
      (float right-margin)
      (float top-margin)
      (float (if total-pages? (+ 20 bottom-margin) bottom-margin))))
   
    (if title (.addTitle doc title))
    (if subject (.addSubject doc subject))
    (if (and nom head) (.addHeader doc nom head))
    (if author (.addAuthor doc author))
    (if creator (.addCreator doc creator))
   
    [doc width height temp-stream output-stream]))
 
(defn- write-total-pages [doc width footer temp-stream output-stream]
  (let [reader    (new PdfReader (.toByteArray temp-stream))
        stamper   (new PdfStamper reader, output-stream)
        num-pages (.getNumberOfPages reader)
        base-font (BaseFont/createFont)]
       
    (dotimes [i num-pages]
      (doto (.getOverContent stamper (inc i))
        (.beginText)
        (.setFontAndSize base-font 10)        
        (.setTextMatrix (float (- width (+ 50 (.getWidthPointKerned base-font footer (float 10))))) (float 20))        
        (.showText (str footer " " (inc i) " of " num-pages))))
    (.close stamper)))
 

(defn write-doc
  "(write-doc document out)
  document consists of a vector containing a map which defines the document metadata and the contents of the document
  out can either be a string which will be treated as a filename or an output stream"
  [[doc-meta & content] out]
   
  (let [[doc width height temp-stream output-stream] (setup-doc doc-meta out)]
    (doseq [item content]
      (append-to-doc (:font doc-meta) width height item doc))
    (.close doc)
    (when (:pages doc-meta) (write-total-pages doc width (:footer doc-meta) temp-stream output-stream))))
 
 
(defn stream-doc
  "reads the document from an input stream one form at a time and writes it out to the output stream
   NOTE: setting the :pages to true in doc meta will require the entire document to remain in memory for
         post processing!"
  [in out]
  (with-open [r (new PushbackReader (new InputStreamReader in))]
    (binding [*read-eval* false]
      (let [doc-meta (read r nil nil)
            [doc width height temp-stream output-stream] (setup-doc doc-meta out)]
        (loop []
          (if-let [item (read r nil nil)]           
            (do
              (append-to-doc (:font doc-meta) width height item doc)
              (recur))
            (do
              (.close doc)
              (when (:pages doc-meta) (write-total-pages doc width (:footer doc-meta) temp-stream output-stream)))))))))
