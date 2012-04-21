(ns clj-pdf.core
  (:use [clojure.set :only (rename-keys)]) 
  (:require [clj_pdf.charting :as charting])
  (:import [com.lowagie.text
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
            RomanList
            Table
	    ZapfDingbatsList
	    ZapfDingbatsNumberList]
           com.lowagie.text.pdf.PdfWriter
           java.awt.Color          
           java.io.FileOutputStream))

(declare make-section)


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
                {:style {:size 18 :style "bold"}})]     
    (make-section (into [:paragraph style] content))))

(defn- paragraph [{indent        :indent
                   style         :style
                   keep-together :keep-together} content]
  (let [paragraph (if style
                    (new Paragraph (make-section content) (font style))
                    (new Paragraph (make-section content)))]
    (if keep-together (.setKeepTogether paragraph true))
    (if indent (.setFirstLineIndent paragraph (float indent)))
    paragraph))


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
  [font-style & content]
  (doto (new Phrase) 
    (.setFont (font font-style))
    (.addAll (map make-section content))))


(defn- text-chunk [font-style content]
  (new Chunk (make-section content) (font font-style)))


(defn- annotation [title text] (new Annotation title text))


(defn- anchor [{style   :style
                leading :leading} 
               content]
  (cond (and style leading) (new Anchor (float leading) content (font style))
        leading             (new Anchor (float leading) (make-section content))
        style               (new Anchor content (font style))
        :else               (new Anchor (make-section content))))

(defn- cell [element]  
  (if (string? element) element
    (let [meta? (map? (second element))
          content (last element)
          c (if (string? content) (new Cell content) (new Cell))]
      
      (if meta?
        (let [{[r g b] :color
               colspan :colspan
               rowspan :rowspan} (second element)]
          
          (if (and r g b) (.setBackgroundColor c (new Color (int r) (int g) (int b))))
          (if rowspan (.setRowspan c (int rowspan)))
          (if colspan (.setColspan c (int colspan)))))      
      (if (string? content) c (doto c (.addElement (make-section content)))))))



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

(defn- table [{[r g b]    :color       
               spacing    :spacing 
               padding    :padding
               header     :header} & rows]
  
  (when rows
    (let [cols  (apply max (map count rows))
          tbl   (new Table cols (count rows))]
      
      (if (and r g b) (.setBackgroundColor tbl (new Color (int r) (int g) (int b))))
      (.setPadding tbl (if padding (float padding) (float 3)))
      (if spacing (.setSpacing tbl (float spacing)))
      (table-header tbl header cols)
      
      (doseq [row rows]        
        (doseq [column row]
          (.addCell tbl (cell column))))
      tbl)))

(defn- chart [& params]  
  (let [width (:width (first params))
        height (:height (first params))] 
    (doto (Image/getInstance (apply charting/chart params) nil)
      (.scaleToFit  (float (* width 0.9)) (float (* height 0.9)))    
      (.setDpi 150 150))))

(defn- make-section
  ([element] (make-section {} element))
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
            :list       li
            :paragraph  paragraph            
            :phrase     phrase
            :table      table)
          (cons params elements))))))


(defn write-doc 
  "(write-doc document out) 
   document consists of a vector containing a map which defines the document metadata and the contents of the document
   
   out can either be a string which will be treated as a filename or an output stream"
  [[{left-margin   :left-margin
     right-margin  :right-margin
     top-margin    :top-margin
     bottom-margin :bottom-margin
     title         :title
     style         :style
     subject       :subject
     [nom head]    :doc-header
     header        :header
     footer        :footer
     author        :author
     creator       :creator
     size          :size
     font-style    :font
     orientation   :orientation}
    & content] out]
    (let [doc (new Document (page-orientation (page-size size) orientation))
          width  (.. doc getPageSize getWidth)
          height (.. doc getPageSize getHeight)]
    (PdfWriter/getInstance
      doc
      (if (string? out) (new FileOutputStream out) out))
    
    ;header and footer must be set before the doc is opened, or itext will not put them on the first page!
    (if header (.setHeader doc (doto (new HeaderFooter (new Phrase header) false)
                                 (.setBorderWidthTop 0))))
    
    (if footer (.setFooter doc 
                 (doto (new HeaderFooter (new Phrase (str footer " ") (font {:size 10})), true)
                   (.setBorder 0)
                   (.setAlignment 2))))
    
    (.open doc)
    (if (and left-margin right-margin top-margin bottom-margin)
      (.setMargins doc
        (float left-margin)
        (float right-margin)
        (float top-margin)
        (float bottom-margin)))
    
    (if title (.addTitle doc title))
    (if subject (.addSubject doc subject))
    (if (and nom head) (.addHeader doc nom head))
    (if author (.addAuthor doc author))
    (if creator (.addCreator doc creator))
    
      
    (doseq [item content]
      (if-let [section (make-section {:style font-style :width width :height height} item)] 
        (.add doc section)))
    (.close doc)))