(ns clj-pdf.core
  (:use [clojure.set :only (rename-keys)])
  (:require [clj_pdf.charting :as charting])
  (:import
    java.awt.Color
    [com.lowagie.text.pdf.draw DottedLineSeparator LineSeparator]  
    sun.misc.BASE64Decoder
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
     Section
     Table
     ZapfDingbatsList
     ZapfDingbatsNumberList]
    [com.lowagie.text.pdf BaseFont PdfContentByte PdfReader PdfStamper PdfWriter]
    [java.io PushbackReader InputStream InputStreamReader FileOutputStream ByteArrayOutputStream]))

(declare make-section)

(defn get-alignment [align]
  (condp = (when align (name align)) "left" 0, "center" 1, "right" 2, 0))

(defn- font
  [{style   :style
    size    :size
    [r g b] :color
    family  :family}]
  (new Font
       (condp = (when family (name family))
         "courier"      (Font/COURIER)
         "helvetica"    (Font/HELVETICA)
         "times-roman"  (Font/TIMES_ROMAN)
         "symbol"       (Font/SYMBOL)
         "zapfdingbats" (Font/ZAPFDINGBATS)
         (Font/HELVETICA))
       (float (or size 10))
       
       (condp = (when style (name style))
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
  (condp = (when size (name size))
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
    (condp = (if orientation (name orientation))
      "landscape"    (.rotate page-size)
      page-size)))

 
(defn- chapter [meta & [title & sections]] 
  (let [ch (new ChapterAutoNumber 
                (make-section meta (if (string? title) [:paragraph title] title)))]    
    (doseq [section sections]
      (make-section (assoc meta :parent ch) section))
    ch))


(defn- heading [meta & content]  
  (make-section
    (into [:paragraph (merge {:size 18 :style :bold} meta)] content)))


(defn- paragraph [meta & content]
  (let [paragraph (new Paragraph)
        {:keys [indent style keep-together leading align]} meta]
        
    (.setFont paragraph (font meta))
    (if keep-together (.setKeepTogether paragraph true))
    (if indent (.setFirstLineIndent paragraph (float indent)))
    (if leading (.setLeading paragraph (float leading)))
    (if align (.setAlignment paragraph (get-alignment align)))
        
    (doseq [item content]
      (.add paragraph 
        (make-section 
          meta 
          (if (string? item) [:chunk item] item))))
    
    paragraph ))


(defn- li [{:keys [numbered lettered roman greek dingbats dingbats-char-num dingbatsnumber dingbatsnumber-type]} & items]
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
            (.addAll (map (partial make-section meta) content)))] 
    (if leading (.setLeading p (float leading))) p))
 

(defn- text-chunk [style content]
  (let [ch (new Chunk (make-section content) (font style))]     
    (cond 
      (:super style) (.setTextRise ch (float 5))
      (:sub style) (.setTextRise ch (float -4))
      :else ch)))


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


(defn- get-border [borders]
  (reduce + 
    (vals 
      (select-keys 
        {:top Cell/TOP :bottom Cell/BOTTOM :left Cell/LEFT :right Cell/RIGHT}
        borders))))



(defn- cell [element]  
  (cond
    (string? element)
    element
    (= "cell" (name (first element)))
    (let [meta? (map? (second element))
          content (last element)
          c (if (string? content) (new Cell content) (new Cell))]
      
      (if meta?
        (let [{:keys [color 
                      colspan 
                      rowspan 
                      border 
                      align 
                      set-border 
                      border-width 
                      border-width-bottom 
                      border-width-left 
                      border-width-right 
                      border-width-top]} (second element)
              [r g b] color]
          
          (if (and r g b) (.setBackgroundColor c (new Color (int r) (int g) (int b))))
          (when (not (nil? border))
            (.setBorder c (if border Rectangle/BOX Rectangle/NO_BORDER)))
            
          (if rowspan (.setRowspan c (int rowspan)))
          (if colspan (.setColspan c (int colspan)))          
          (if set-border (.setBorder c (int (get-border set-border))))          
          (if border-width (.setBorderWidth c (float border-width)))
          (if border-width-bottom (.setBorderWidthBottom c (float border-width-bottom)))
          (if border-width-left (.setBorderWidthLeft c (float border-width-left)))
          (if border-width-right (.setBorderWidthRight c  (float border-width-right)))
          (if border-width-top (.setBorderWidthTop c (float border-width-top)))
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
 
 
(defn- table [{:keys [color spacing padding offset header border border-width cell-border width widths align title num-cols]}
              & rows]
  (when (< (count rows) 1) (throw (new Exception "Table must contain rows!")))
  
  (let [cols (or num-cols (apply max (map count rows)))
        tbl   (doto (new Table cols (count rows)) (.setWidth (float (or width 100))))]
    
    (when widths
      (if (= (count widths) cols) 
        (.setWidths tbl (int-array widths))
        (throw (new Exception (str "wrong number of columns specified in widths: " widths ", number of columns: " cols)))))

    (if (= false border)
      (.setBorder tbl Rectangle/NO_BORDER)
      (when border-width (.setBorderWidth tbl (float border-width))))
 
    (when (= false cell-border)
      (.setDefaultCell tbl (doto (new Cell) (.setBorder Rectangle/NO_BORDER))))
   
    (if color (let [[r g b] color] (.setBackgroundColor tbl (new Color (int r) (int g) (int b)))))
    (.setPadding tbl (if padding (float padding) (float 3)))
    (if spacing (.setSpacing tbl (float spacing)))
    (if offset (.setOffset tbl (float offset)))
    (table-header tbl header cols)
 
    (.setAlignment tbl (get-alignment align))
   
    (doseq [row rows]
      (doseq [column row]
        (.addCell tbl (cell column))))
    
    tbl))


(defn- image [{:keys [xscale        
                      yscale        
                      align         
                      width         
                      height        
                      base64?       
                      annotation
                      pad-left      
                      pad-right     
                      left-margin   
                      right-margin  
                      top-margin    
                      bottom-margin 
                      page-width    
                      page-height]}              
              img-data]
  (let [img (cond
              (instance? java.awt.Image img-data)
              (Image/getInstance (.createImage (java.awt.Toolkit/getDefaultToolkit) (.getSource img-data)) nil)
              
              base64?
              (Image/getInstance (.createImage (java.awt.Toolkit/getDefaultToolkit) (.decodeBuffer (new BASE64Decoder) img-data)) nil)
                            
              (= Byte/TYPE (.getComponentType (class img-data)))
              (Image/getInstance (.createImage (java.awt.Toolkit/getDefaultToolkit) img-data) nil)
              
              (or (string? img-data) (instance? java.net.URL img-data))
              (Image/getInstance img-data)
              
              :else
              (throw (new Exception (str "Unsupported image data: " img-data ", must be one of java.net.URL, java.awt.Image, or filename string"))))
        img-width (.getWidth img)
        img-height (.getHeight img)]
    
    (when align (.setAlignment img (get-alignment align)))
    (when annotation (let [[title text] annotation] (.setAnnotation img (make-section [:annotation title text]))))
    (when pad-left (.setIndentationLeft img (float pad-left)))
    (when pad-right (.setIndentationRight img (float pad-right)))
    
    ;;scale relative to page size
    (when (and page-width page-height left-margin right-margin top-margin bottom-margin)
      (let [available-width (- page-width (+ left-margin right-margin))
            available-height (- page-height (+ top-margin bottom-margin))
            page-scale (* 100 (if (> img-width img-height)
                                (/ available-width img-width)
                                (/ available-height img-height)))]
        (cond
          (and xscale yscale) (.scalePercent img (float (* page-scale xscale)) (float (* page-scale yscale)))
          xscale (.scalePercent img (float (* page-scale xscale)) (float 100))
          yscale (.scalePercent img (float 100) (float (* page-scale yscale)))
          :else (when (or (>  img-width available-width) (>  img-height available-height))
                  (.scalePercent img (float page-scale))))))
        
    (when (and width height) (.scaleToFit img (float width) (float height)))
    img))


(defn- section [meta & [title & content]]  
  (let [sec (.addSection (:parent meta) 
              (make-section meta (if (string? title) [:paragraph title] title)))
        indent (:indent meta)]
    (if indent (.setIndentation sec (float indent)))
    (doseq [item content]
      (if (and (coll? item) (= "section" (name (first item)))) 
        (make-section (assoc meta :parent sec) item)
        (.add sec (make-section meta (if (string? item) [:chunk item] item)))))))


(defn- subscript [meta text]
  (text-chunk (assoc meta :sub true) text))


(defn- superscript [meta text]
  (text-chunk (assoc meta :super true) text))


(defn- chart [& params]  
  (let [meta (first params)
        {:keys [page-width page-height]} meta] 
    (image (assoc meta 
                  :align :center
                  :width (* 0.85 page-width) 
                  :height (* 0.85 page-height)) 
           (apply charting/chart params))))
 
(defn- line [{dotted? :dotted, gap :gap} & args]
  (doto (if dotted?
          (if gap 
            (doto (new DottedLineSeparator) (.setGap (float gap)))
            (new DottedLineSeparator))
          (new LineSeparator)) 
    (.setOffset -5)))
 

(defn- spacer
  ([_] (make-section [:paragraph {:leading 12} "\n"]))
  ([_ height]
    (make-section [:paragraph {:leading 12} (apply str (take height (repeat "\n")))])))


(defn- make-section
  ([element] (if element (make-section {} element) ""))
  ([meta element]
    (cond 
      (string? element) element
      (nil? element) ""
      (number? element) (str element)
      :else
      (let [[element-name & content] element
            tag (if (string? element-name) (keyword element-name) element-name)
            params? (map? (first content))
            params (if  params? (merge meta (first content)) meta)
            elements (if params? (rest content) content)]
       
        (apply
          (condp = tag
            :anchor      anchor
            :annotation  annotation
            :cell        cell
            :chapter     chapter
            :chart       chart
            :chunk       text-chunk
            :heading     heading
            :image       image
            :line        line
            :list        li
            :paragraph   paragraph
            :phrase      phrase
            :section     section
            :spacer      spacer
            :superscript superscript
            :subscript   subscript
            :table       table
            (throw (new Exception (str "invalid tag: " tag " in element: " element) )))
          (cons params elements))))))
 
 (defn append-to-doc [font-style width height item doc]
  (if-let [section (make-section
                     (assoc font-style
                            :left-margin (.leftMargin doc)
                            :right-margin (.rightMargin doc)
                            :top-margin (.topMargin doc)
                            :bottom-margin (.bottomMargin doc)
                            :page-width width 
                            :page-height height)
                      item)]
    (.add doc section)))
 
 (defn- add-header [header doc]
   (if header
          (.setHeader doc
            (doto (new HeaderFooter (new Phrase header) false) (.setBorderWidthTop 0)))))
 
(defn setup-doc [{:keys [left-margin  
                         right-margin  
                         top-margin    
                         bottom-margin 
                         title                           
                         subject       
                         doc-header    
                         header        
                         letterhead    
                         footer        
                         pages  
                         author        
                         creator       
                         size          
                         font-style    
                         orientation]}                 
                  out]
 
  (let [[nom head] doc-header
        doc    (new Document (page-orientation (page-size size) orientation))
        width  (.. doc getPageSize getWidth)
        height (.. doc getPageSize getHeight)
        output-stream (if (string? out) (new FileOutputStream out) out)
        temp-stream   (if pages (new ByteArrayOutputStream))]
 
    ;;header and footer must be set before the doc is opened, or itext will not put them on the first page!
    ;;if we have to print total pages, then the document has to be post processed
    (if pages
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
        (doseq [item letterhead]
          (append-to-doc  (or font-style {})  width height (if (string? item) [:paragraph item] item) doc))
        (add-header header doc))
      (do
        (add-header header doc)
        (.open doc)))
   
    (if (and left-margin right-margin top-margin bottom-margin)
    (.setMargins doc
      (float left-margin)
      (float right-margin)
      (float top-margin)
      (float (if pages (+ 20 bottom-margin) bottom-margin))))
   
    (if title (.addTitle doc title))
    (if subject (.addSubject doc subject))
    (if (and nom head) (.addHeader doc nom head))
    (if author (.addAuthor doc author))
    (if creator (.addCreator doc creator))
   
    [doc width height temp-stream output-stream]))
 
(defn write-total-pages [doc width {:keys [footer footer-separator]} temp-stream output-stream]  
  (let [reader    (new PdfReader (.toByteArray temp-stream))
        stamper   (new PdfStamper reader, output-stream)
        num-pages (.getNumberOfPages reader)
        base-font (BaseFont/createFont)]
       
    (dotimes [i num-pages]
      (doto (.getOverContent stamper (inc i))
        (.beginText)
        (.setFontAndSize base-font 10)        
        (.setTextMatrix (float (- width (+ 50 (.getWidthPointKerned base-font footer (float 10))))) (float 20))        
        (.showText (str footer " " (inc i) (or footer-separator " / ") num-pages))
        (.endText)))
    (.close stamper)))
 

(defn- preprocess-item [item]  
  (cond
    (string? item) 
    [:paragraph item]
    
    (= :table (first item))
    ;;iText page breaks on tables are broken,
    ;;this ensures that table will not spill over other content
    [:paragraph {:leading 20} item]
    
    :else item))

(defn write-doc
  "(write-doc document out)
  document consists of a vector containing a map which defines the document metadata and the contents of the document
  out can either be a string which will be treated as a filename or an output stream"
  [[doc-meta & content] out]
  
  (let [[doc width height temp-stream output-stream] (setup-doc doc-meta out)]
    (doseq [item content]
      (append-to-doc (:font doc-meta) width height (preprocess-item item) doc))
    (.close doc)
    (when (:pages doc-meta) (write-total-pages doc width doc-meta temp-stream output-stream))))
 
(defn to-pdf [input-reader r out]
  (let [doc-meta (input-reader r)
        [doc width height temp-stream output-stream] (clj-pdf.core/setup-doc doc-meta out)] 
    (loop []
      (if-let [item (input-reader r)] 
        (do
          (append-to-doc (:font doc-meta) width height (preprocess-item item) doc)
          (recur))
        (do 
          (.close doc)
          (when (:pages doc-meta) (write-total-pages doc width doc-meta temp-stream output-stream)))))))

(defn stream-doc
  "reads the document from an input stream one form at a time and writes it out to the output stream
   NOTE: setting the :pages to true in doc meta will require the entire document to remain in memory for
         post processing!"
  [in out]
  (with-open [r (new PushbackReader (new InputStreamReader in))]
    (binding [*read-eval* false]
      (to-pdf (fn [r] (read r nil nil)) out))))


(defn pdf
  "usage:
   in can be either a vector containing the document or an input stream. If in is an input stream then the forms will be read sequentially from it.
   out can be either a string, in which case it's treated as a file name, or an output stream.
   NOTE: using the :pages option will cause the complete document to reside in memory as it will need to be post processed."
  [in out]
  (if (instance? InputStream in)
    (stream-doc in out)
    (write-doc in out)))