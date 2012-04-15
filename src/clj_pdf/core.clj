(ns clj-pdf.core
 (:import [com.lowagie.text
           Anchor
           Annotation
           ChapterAutoNumber
           Chunk
           Document
           Font
           List
           ListItem
           PageSize
           Paragraph
           Phrase
           RomanList]
          java.awt.Color
          com.lowagie.text.pdf.PdfWriter
          java.io.FileOutputStream))

(declare make-section)

(defn font [{style   :style
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

(defn- paragraph [{indent        :indent
                   keep-together :keep-together} content]
  (let [paragraph (new Paragraph (make-section content))]
    (if keep-together (.setKeepTogether paragraph true))
    (if indent (.setFirstLineIndent paragraph (float indent)))
    paragraph))

(defn- li [{numbered :numbered
            lettered :lettered
            roman    :roman}
           & items]
  (let [list (if roman (new RomanList)
               (new List (or numbered false) (or lettered false)))]
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

(defn- make-section [element]
  (if (string? element)
    element
    (let [[tag & content] element
          params? (map? (first content))
          params (if  params? (first content) {})
          elements (if params? (rest content) content)]
      
      (apply
        (condp = tag
          :anchor     anchor
          :annotation annotation
          :chapter    chapter
          :chunk      text-chunk
          :list       li
          :paragraph  paragraph
          :phrase     phrase)
        (cons params elements)))))

(defn write-doc 
  "(write-doc document out) 
   document consists of a vector containing a map which defines the document metadata and the contents of the document
   
   out can either be a string which will be treated as a filename or an output stream"
  [[{left-margin   :left-margin
     right-margin  :right-margin
     top-margin    :top-margin
     bottom-margin :bottom-margin
     title         :title
     subject       :subject
     header        :header
     author        :author
     creator       :creator
     size          :size
     orientation   :orientation}
    & content] out]
    (let [doc (new Document (page-orientation (page-size size) orientation))]
    (PdfWriter/getInstance
      doc
      (if (string? out) (new FileOutputStream out) out))
    (.open doc)
    (if (and left-margin right-margin top-margin bottom-margin)
      (.setMargins doc
        (float left-margin)
        (float right-margin)
        (float top-margin)
        (float bottom-margin)))
    
    (if title (.addTitle doc title))
    (if subject (.addSubject doc subject))
    (if header (.addHeader doc (first header) (second header)))
    (if author (.addAuthor doc author))
    (if creator (.addCreator doc creator))
    (doseq [item content]
      (.add doc (make-section item)))
    (.close doc)))


