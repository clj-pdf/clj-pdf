(ns clj-pdf.core
 (:import [com.lowagie.text           
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
        :courier   (Font/COURIER)
        :helvetica (Font/HELVETICA)
        :times-roman (Font/TIMES_ROMAN)
        :symbol      (Font/SYMBOL)
        :zapfdingbats (Font/ZAPFDINGBATS)
        (Font/HELVETICA))
      (float (if size size 11))
      (condp = style
        :bold (Font/BOLD)
        :italic (Font/ITALIC)
        :bold-italic (Font/BOLDITALIC)
        :normal (Font/NORMAL)
        :strikethru (Font/STRIKETHRU)
        :underline (Font/UNDERLINE)
        (Font/NORMAL))
      (if (and r g b)
        (new Color r g b)
        (new Color 0 0 0))))

(defn- chapter [title] (new ChapterAutoNumber (make-section title)))

(defn- paragraph [content]
 (new Paragraph (make-section content)))

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

(defn- make-section [element]
 (if (string? element)
   element
   (let [[tag & content] element]
     (apply
       (condp = tag
         :chapter   chapter
         :chunk     text-chunk
         :list      li
         :paragraph paragraph
         :phrase    phrase)
       content))))


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
     creator       :creator}
    & content] out]
  (let [doc (new Document)]
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

