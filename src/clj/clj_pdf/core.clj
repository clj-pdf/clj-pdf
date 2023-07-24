(ns clj-pdf.core
  (:require
    [clojure.java.io :as io]
    [clojure.walk]
    [clj-pdf.graphics-2d :as g2d]
    [clj-pdf.section :refer [make-section *cache*]]
    [clj-pdf.utils :refer [get-alignment get-color flatten-seqs font]])
  (:import
    [java.awt Color Graphics2D Toolkit Canvas]
    [java.awt.image BufferedImage]
    [java.io PushbackReader InputStream InputStreamReader OutputStream
             FileOutputStream ByteArrayOutputStream File]
    [javax.imageio ImageIO]
    [com.lowagie.text Chunk Document HeaderFooter Phrase Rectangle RectangleReadOnly
                      PageSize Font FontFactory Paragraph Image]
    [com.lowagie.text.pdf BaseFont PdfContentByte PdfReader PdfStamper PdfWriter PdfCopy
                     PdfPageEventHelper PdfPCell PdfPTable]))

(declare ^:dynamic *pdf-writer*)
(def fonts-registered? (atom nil))

(defn- custom-page-size [width height]
  (RectangleReadOnly. width height))


(defn- page-size [size]
  (if (vector? size)
    (apply custom-page-size size)
    (case (when size (name size))
      "a0" PageSize/A0
      "a1" PageSize/A1
      "a2" PageSize/A2
      "a3" PageSize/A3
      "a4" PageSize/A4
      "a5" PageSize/A5
      "a6" PageSize/A6
      "a7" PageSize/A7
      "a8" PageSize/A8
      "a9" PageSize/A9
      "a10" PageSize/A10
      "arch-a" PageSize/ARCH_A
      "arch-b" PageSize/ARCH_B
      "arch-c" PageSize/ARCH_C
      "arch-d" PageSize/ARCH_D
      "arch-e" PageSize/ARCH_E
      "b0" PageSize/B0
      "b1" PageSize/B1
      "b2" PageSize/B2
      "b3" PageSize/B3
      "b4" PageSize/B4
      "b5" PageSize/B5
      "b6" PageSize/B6
      "b7" PageSize/B7
      "b8" PageSize/B8
      "b9" PageSize/B9
      "b10" PageSize/B10
      "crown-octavo" PageSize/CROWN_OCTAVO
      "crown-quarto" PageSize/CROWN_QUARTO
      "demy-octavo" PageSize/DEMY_OCTAVO
      "demy-quarto" PageSize/DEMY_QUARTO
      "executive" PageSize/EXECUTIVE
      "flsa" PageSize/FLSA
      "flse" PageSize/FLSE
      "halfletter" PageSize/HALFLETTER
      "id-1" PageSize/ID_1
      "id-2" PageSize/ID_2
      "id-3" PageSize/ID_3
      "large-crown-octavo" PageSize/LARGE_CROWN_OCTAVO
      "large-crown-quarto" PageSize/LARGE_CROWN_QUARTO
      "ledger" PageSize/LEDGER
      "legal" PageSize/LEGAL
      "letter" PageSize/LETTER
      "note" PageSize/NOTE
      "penguin-large-paperback" PageSize/PENGUIN_LARGE_PAPERBACK
      "penguin-small-paperback" PageSize/PENGUIN_SMALL_PAPERBACK
      "postcard" PageSize/POSTCARD
      "royal-octavo" PageSize/ROYAL_OCTAVO
      "royal-quarto" PageSize/ROYAL_QUARTO
      "small-paperback" PageSize/SMALL_PAPERBACK
      "tabloid" PageSize/TABLOID
      PageSize/A4)))


(defn- page-orientation [^Rectangle page-size orientation]
  (if page-size
    (condp = (if orientation (name orientation))
      "landscape" (.rotate page-size)
      page-size)))


(declare append-to-doc)

(defn- clear-double-page [stylesheet references font-style width height item doc ^PdfWriter pdf-writer]
  "End current page and make sure that subsequent content will start on
     the next odd-numbered page, inserting a blank page if necessary."
  (let [append (fn [item] (append-to-doc stylesheet references font-style width height item doc pdf-writer))]
    ;; Inserting a :pagebreak starts a new page, unless we already happen to
    ;; be on a blank page, in which case it does nothing;
    (append [:pagebreak])
    ;; in either case we're now on a blank page, and if it's even-numbered,
    ;; we need to insert some whitespace to force the next :pagebreak to start
    ;; a new, odd-numbered page.
    (when (even? (.getPageNumber pdf-writer))
      (append [:paragraph " "])
      (append [:pagebreak]))))

(defn- append-to-doc [stylesheet references font-style width height item ^Document doc ^PdfWriter pdf-writer]
  (cond
    (= [:pagebreak] item) (.newPage doc)
    (= [:clear-double-page] item) (clear-double-page stylesheet references font-style width height item doc pdf-writer)
    :else (.add doc
                (make-section
                  (assoc font-style
                    :stylesheet stylesheet
                    :references references
                    :left-margin (.leftMargin doc)
                    :right-margin (.rightMargin doc)
                    :top-margin (.topMargin doc)
                    :bottom-margin (.bottomMargin doc)
                    :page-width width
                    :page-height height
                    :pdf-writer pdf-writer)
                  (or item [:paragraph item])))))

(defn- add-header [header ^Document doc font-style]
  (when header
    (if (= (first header) :image)
      (.setHeader doc
                    (doto (new HeaderFooter (new Phrase (new Chunk (Image/getInstance ^String (second header)) 0.0 0.0)) false)
                    (.setBorderWidthTop 0)))
      (.setHeader doc
                  (doto (new HeaderFooter (new Phrase ^String header ^Font (font font-style)) false)
                    (.setBorderWidthTop 0))))))

(defn set-header-footer-table-width [table ^Document doc page-numbers?]
  (let [default-width (- (.right doc) (.left doc) (if page-numbers? 20 0))]
    (if (map? (second table))
      (update-in table [1 :width] #(or % default-width))
      (concat [(first table)] [{:width default-width}] (rest table)))))

(defn- get-header-footer-table-section [table-content meta ^Document doc page-numbers? footer?]
  (as-> table-content x
        (set-header-footer-table-width x doc (and footer? page-numbers?))
        ;; :header and :footer are different for the root document meta map vs. the meta map
        ;; that is expected for :pdf-table (which is what 'x' here should be at this point)
        ;; TODO: remove other possible map key conflicts? i think these are the only 2 ...
        (make-section (dissoc meta :header :footer) x)))

;; FIXME: unused?
(defn table-header-footer-height [content meta ^Document doc page-numbers? footer?]
  (let [table        (get-header-footer-table-section (:table content) meta doc page-numbers? footer?)
        table-height (.getTotalHeight ^PdfPTable table)]
    table-height))

(defn set-margins [^Document doc left-margin right-margin top-margin bottom-margin page-numbers?]
  (let [margins {:left   (or left-margin (.leftMargin doc))
                 :right  (or right-margin (.rightMargin doc))
                 :top    (if top-margin
                           (+ top-margin (.topMargin doc))
                           (.topMargin doc))
                 :bottom (+ (if page-numbers? 20 0)
                            (or bottom-margin (.bottomMargin doc)))}]
    (.setMargins doc (float (:left margins)) (float (:right margins)) (float (:top margins)) (float (:bottom margins)))
    margins))

(defn write-header-footer-content-row [{:keys [table x y]} ^PdfWriter writer]
  (.writeSelectedRows ^PdfPTable table (int 0) (int -1) (float x) (float y) (.getDirectContent writer)))

(defn table-footer-header-event [header-content footer-content margins header-first-page?]
  (proxy [PdfPageEventHelper] []
    (onEndPage [^PdfWriter writer ^Document doc]
      (let [page-num     (.getPageNumber doc)
            first-page?  (= page-num 1)
            show-header? (and (boolean header-content)
                              (or (not first-page?) header-first-page?))
            show-footer? (boolean footer-content)]

        ;; set top margin ready for header on next page
        (when header-content
          (let [top-margin (+ (:top margins)
                              (.getTotalHeight ^PdfPTable (:table header-content)))]
            (.setMargins doc (:left margins) (:right margins) top-margin (:bottom margins))))

        ; write header and/or footer tables to appropriate places on the page if they are set and required by the
        ; current state (e.g. use of a letterhead when on page #1 will mean no header even if one is set)
        (if show-header? (write-header-footer-content-row header-content writer))
        (if show-footer? (write-header-footer-content-row footer-content writer))))))

(defn preprocess-header-footer-content [content meta ^Document doc footer? page-numbers?]
  (let [table  (get-header-footer-table-section (:table content) meta doc page-numbers? footer?)
        height (.getTotalHeight ^PdfPTable table)
        y      (if footer?
                 (+ (.bottom doc) height)
                 (.top doc))]
    (-> content
        (assoc-in [:table] table)
        (update-in [:x] #(or % (.left doc)))
        (update-in [:y] #(or % y))
        (update-in [:height] #(or % height)))))

(defn set-table-header-footer-event [header-content footer-content meta doc margins page-numbers? ^PdfWriter pdf-writer header-first-page?]
  (let [header-content (if header-content (preprocess-header-footer-content header-content meta doc false page-numbers?))
        footer-content (if footer-content (preprocess-header-footer-content footer-content meta doc true page-numbers?))]
    (.setPageEvent pdf-writer (table-footer-header-event header-content footer-content margins header-first-page?))
    (when header-content {:header-content header-content
                          :footer-content footer-content})))

(defn page-events? [{:keys [pages page-events]}]
  (or pages (not (empty? page-events))))

(defn buffered-image [img-data]
  (cond
    (string? img-data)
    (ImageIO/read (File. ^String img-data))

    (instance? BufferedImage img-data)
    img-data))

(defn watermark-stamper [meta]
  (let [image (some-> (-> meta :watermark :image) buffered-image)]
    (proxy [PdfPageEventHelper] []
      (onEndPage [writer doc]
        (let [{:keys [scale rotate translate under] :or {under true} :as wm} (:watermark meta)]
          (g2d/with-graphics
            (assoc meta
              :pdf-writer writer
              :under under
              :scale scale
              :rotate rotate
              :translate translate)
            (or (:render wm)
                (fn [^Graphics2D g2d]
                  (.drawImage
                    g2d
                    ^BufferedImage image
                    nil
                    (int 0)
                    (int 0))))))))))

(defn doc-events [^PdfWriter pdf-writer
                  {:keys [on-document-open
                          on-document-close
                          on-page-start
                          on-page-end
                          on-chapter-start
                          on-chapter-end
                          on-paragraph-start
                          on-paragraph-end
                          on-section-start
                          on-section-end
                          event-handler]}]
  (if event-handler
    (.setPageEvent pdf-writer ^PdfPageEventHelper event-handler)

    (when (not-empty (remove nil? [on-document-open
                                   on-document-close
                                   on-page-start
                                   on-page-end
                                   on-chapter-start
                                   on-chapter-end
                                   on-paragraph-start
                                   on-paragraph-end
                                   on-section-start
                                   on-section-end
                                   event-handler]))
      (.setPageEvent
        pdf-writer
        ^PdfPageEventHelper
        (proxy [PdfPageEventHelper] []
          (onOpenDocument [^PdfWriter writer ^Document doc]
            (when on-document-open
              (on-document-open writer ^Document doc)))

          (onCloseDocument [^PdfWriter writer ^Document doc]
            (when on-document-close
              (on-document-close writer doc)))

          (onStartPage [^PdfWriter writer ^Document doc]
            (when on-page-start
              (on-page-start writer doc)))

          (onEndPage [^PdfWriter writer ^Document doc]
            (when on-page-end
              (on-page-end writer doc)))

          (onChapter [^PdfWriter writer ^Document doc position ^Paragraph title]
            (when on-chapter-start
              (on-chapter-start writer doc position title)))

          (onChapterEnd [^PdfWriter writer ^Document doc position]
            (when on-chapter-end
              (on-chapter-end writer doc position)))

          (onParagraph [^PdfWriter writer ^Document doc position]
            (when on-paragraph-start
              (on-paragraph-start writer ^Document doc position)))

          (onParagraphEnd [^PdfWriter writer ^Document doc position]
            (when on-paragraph-end
              (on-paragraph-end writer doc position)))

          (onSection [^PdfWriter writer ^Document doc position depth ^Paragraph title]
            (when on-section-start
              (on-section-start writer doc position depth title)))

          (onSectionEnd [^PdfWriter writer ^Document doc position]
            (when on-section-end
              (on-section-end writer doc position))))))))

(defn background-color-applier [background-color]
  (proxy [PdfPageEventHelper] []
    (onEndPage [^PdfWriter writer ^Document doc]
      (let [^PdfContentByte canvas (.getDirectContentUnder writer)
            ^Rectangle rect        (.getPageSize doc)]
        (.setColorFill canvas (get-color background-color))
        (.rectangle canvas (.getLeft rect) (.getBottom rect) (.getWidth rect) (.getHeight rect))
        (.fill canvas)))))

(defn set-initial-margins [^Document doc header-content footer-content margins header-first-page?]
  (let [has-footer? (boolean footer-content)
        left        (:left margins)
        right       (:right margins)
        top         (if header-first-page?
                      (if (and (:height header-content) (:y header-content))
                        (+ (:height header-content) (- (.top doc) (:y header-content)) (:top margins))
                        (if (:height header-content)
                          (+ (:height header-content) (:top margins))
                          (:top margins)))
                      (:top margins))
        bottom      (if has-footer?
                      (+ (:bottom margins) (:height footer-content))
                      (:bottom margins))]
    ;; only set the top margin to make space for the header if it is to be present on ALL pages
    ;; otherwise this will be sorted out in the `on-page-end` event handler
    ;; this is to ensure letterheads get rendered at the correct position
    (.setMargins doc (float left) (float right) (float top) (float bottom))))

(defn- setup-doc [{:keys [left-margin
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
                          keywords
                          creator
                          size
                          font-style
                          orientation
                          page-events
                          watermark
                          background-color] :as meta}
                  out]

  (let [[nom head] doc-header
        doc                (Document. (page-orientation (page-size size) orientation))
        width              (.. doc getPageSize getWidth)
        height             (.. doc getPageSize getHeight)
        font-style         (or font-style {})
        output-stream      (if (string? out) (FileOutputStream. ^String out) out)
        temp-stream        (if (page-events? meta) (ByteArrayOutputStream.))
        page-numbers?      (and (not= false footer)
                                (not= false (:page-numbers footer)))
        table-header       (if (:table header) header)
        header             (when-not table-header header)
        table-footer       (if (:table footer) footer)
        footer             (when (and (not= footer false) (not table-footer))
                             (if (string? footer)
                               {:text footer :align :right :start-page 1}
                               (merge {:align :right :start-page 1} footer)))
        header-first-page? (and table-header
                                (if letterhead false true))]

    ;;header and footer must be set before the doc is opened, or itext will not put them on the first page!
    ;;if we have to print total pages or add a watermark, then the document has to be post processed
    (let [output-stream-to-use  (if (page-events? meta) temp-stream output-stream)
          pdf-writer            (PdfWriter/getInstance doc output-stream-to-use)
          header-meta           (merge font-style (dissoc meta :size))
          margins               (set-margins doc left-margin right-margin top-margin bottom-margin page-numbers?)
          header-footer-content (set-table-header-footer-event table-header table-footer header-meta doc margins page-numbers? pdf-writer header-first-page?)]

      (when background-color
        (.setPageEvent pdf-writer (background-color-applier background-color)))

      (when watermark
        (.setPageEvent pdf-writer (watermark-stamper (assoc meta
                                                       :page-width width
                                                       :page-height height))))

      (doc-events pdf-writer meta)

      (when-not pages
        (doseq [page-event page-events]
          (.setPageEvent pdf-writer page-event))
        (if (or footer page-numbers?)
          (.setFooter doc
                      (doto (new HeaderFooter (new Phrase (str (:text footer) " ")
                                                   (font (merge font-style {:size 10 :color (:color footer)}))) page-numbers?)
                        (.setBorder 0)
                        (.setAlignment ^int (get-alignment (:align footer)))))))

      ;;must set margins before opening the doc
      (set-initial-margins doc (:header-content header-footer-content) (:footer-content header-footer-content) margins header-first-page?)


      ;;if we have a letterhead then we want to put it on the first page instead of the header,
      ;;so we will open doc before adding the header
      (if letterhead
        (do
          (.open doc)
          (doseq [item letterhead]
            (append-to-doc nil nil font-style width height (if (string? item) [:paragraph item] item) doc pdf-writer))
          (add-header header doc font-style))
        (do
          (add-header header doc font-style)
          (.open doc)))

      (when title (.addTitle doc title))
      (when subject (.addSubject doc subject))
      (when (and nom head) (.addHeader doc nom head))
      (when author (.addAuthor doc author))
      (when keywords (.addKeywords doc keywords))
      (when creator (.addCreator doc creator))

      [doc width height temp-stream output-stream pdf-writer])))

(defn- write-pages [^ByteArrayOutputStream temp-stream ^OutputStream output-stream]
  (.writeTo temp-stream output-stream)
  (.flush output-stream)
  (.close output-stream))

(defn- align-footer [page-width ^BaseFont base-font {:keys [align size]} text]
  (let [font-width (.getWidthPointKerned base-font text (float size))]
    (float
      (condp = align
        :right (- page-width (+ 50 font-width))
        :left (+ 50 font-width)
        :center (- (/ page-width 2) (/ font-width 2))))))

(defn- write-total-pages [width {:keys [total-pages footer font-style]} ^ByteArrayOutputStream temp-stream ^OutputStream output-stream]
  (when (or total-pages (:table footer))
    (let [reader    (new PdfReader (.toByteArray temp-stream))
          stamper   (new PdfStamper reader output-stream)
          num-pages (.getNumberOfPages reader)
          footer    (when (not= footer false)
                      (if (string? footer)
                        (merge {:text footer :align :right :start-page 1 :size 10} font-style)
                        (merge {:align :right :start-page 1 :size 10} font-style footer)))
          font      (font footer)
          base-font (.getBaseFont font)]
      (when (and footer (not= false (-> footer :page-numbers)))
        (dotimes [i num-pages]
          (if (>= i (dec (or (:start-page footer) 1)))
            (let [footer-string (if total-pages
                                  (str (:text footer) " " (inc i) (or (:footer-separator footer) " / ") num-pages)
                                  (str (:text footer) " " (inc i)))]
              (doto (.getOverContent stamper (inc i))
                (.beginText)
                (.setFontAndSize base-font (:size footer))
                (.setColorFill (.getColor font))
                (.setTextMatrix
                  (align-footer width base-font footer footer-string) (float 20))
                (.showText footer-string)
                (.endText))))))
      (.close stamper))))

(defn- preprocess-item [item]
  (cond
    (string? item)
    [:paragraph item]

    ;;iText page breaks on tables are broken,
    ;;this ensures that table will not spill over other content
    (= :table (first item))
    [:paragraph {:leading 20} item]

    :else item))

(defn- add-item [item {:keys [stylesheet font references]} width height ^Document doc ^PdfWriter pdf-writer]
  (if (seq? item)
    (doseq [element (flatten-seqs item)]
      (append-to-doc stylesheet references font width height (preprocess-item element) doc pdf-writer))
    (append-to-doc stylesheet references font width height (preprocess-item item) doc pdf-writer)))

(defn- register-fonts [doc-meta]
  (when (and (= true (:register-system-fonts? doc-meta))
             (nil? @fonts-registered?))
    ;; register fonts in usual directories
    (FontFactory/registerDirectories)
    (g2d/g2d-register-fonts)
    (reset! fonts-registered? true)))

(defn- parse-meta [doc-meta]
  (register-fonts doc-meta)
  ; font would conflict with a function definition
  (-> doc-meta
      (assoc :font-style (:font doc-meta))
      (assoc :total-pages (:pages doc-meta))
      (assoc :pages (boolean (or (:pages doc-meta) (-> doc-meta :footer :table))))))

(defn- write-doc
  "(write-doc document out)
  document consists of a vector containing a map which defines the document metadata and the contents of the document
  out can either be a string which will be treated as a filename or an output stream"
  [[doc-meta & content] out]
  (let [doc-meta (parse-meta doc-meta)
        [^Document doc
         width height
         ^ByteArrayOutputStream temp-stream
         ^OutputStream output-stream
         ^PdfWriter pdf-writer] (setup-doc doc-meta out)]
    (doseq [item content]
      (add-item item doc-meta width height doc pdf-writer))
    (.close doc)
    (when (and (not (:pages doc-meta))
               (not (empty? (:page-events doc-meta))))
      (write-pages temp-stream output-stream))
    (write-total-pages width doc-meta temp-stream output-stream)))

(defn- to-pdf [input-reader r out]
  (let [doc-meta (-> r input-reader parse-meta)
        [^Document doc
         width height
         ^ByteArrayOutputStream temp-stream
         ^OutputStream output-stream
         ^PdfWriter pdf-writer] (setup-doc doc-meta out)]
    (loop []
      (if-let [item (input-reader r)]
        (do
          (add-item item doc-meta width height doc pdf-writer)
          (recur))
        (do
          (.close doc)
          (write-total-pages width doc-meta temp-stream output-stream))))))

(defn- seq-to-doc [items out]
  (let [doc-meta (-> items first parse-meta)
        [^Document doc
         width height
         ^ByteArrayOutputStream temp-stream
         ^OutputStream output-stream
         ^PdfWriter pdf-writer] (setup-doc doc-meta out)]
    (binding [*pdf-writer* pdf-writer]
      (doseq [item (rest items)]
        (add-item item doc-meta width height doc pdf-writer)))
    (.close doc)
    (write-total-pages width doc-meta temp-stream output-stream)))

(defn- stream-doc
  "reads the document from an input stream one form at a time and writes it out to the output stream
   NOTE: setting the :pages to true in doc meta will require the entire document to remain in memory for
         post processing!"
  [in out]
  (with-open [r (new PushbackReader (new InputStreamReader in))]
    (binding [*read-eval* false]
      (to-pdf (fn [r] (read r nil nil)) r out))))

(defn pdf
  "`in` can be either a vector containing the document or an input stream. If in
  is an input stream then the forms will be read sequentially from it. `out` can
  be either a string, in which case it's treated as a file name, or an output
  stream.

  NOTE: using the :pages option will cause the complete document to reside in
  memory as it will need to be post processed."
  [in out]
  (binding [*cache* (atom {})]
    (cond (instance? InputStream in) (stream-doc in out)
          (seq? in) (seq-to-doc in out)
          :else (write-doc in out))))

(defn collate
  "usage: takes an output that can be a file name or an output stream followed by one or more documents
   that can be input streams, urls, filenames, or byte arrays."
  [& params]
  (let [[{:keys [author creator subject title]} out & pdfs]
        (if (map? (first params))
          params
          (into [{}] params))
        out (io/output-stream out)
        doc (Document.)
        wrt (PdfCopy. doc out)]
    (.open doc)
    (.getDirectContent wrt)
    (when title (.addTitle doc title))
    (when subject (.addSubject doc subject))
    (when author (.addAuthor doc author))
    (when creator (.addCreator doc creator))
    (doseq [pdf pdfs]
      (with-open [rdr (PdfReader. (io/input-stream pdf))]
        (dotimes [i (.getNumberOfPages rdr)]
          (let [page (.getImportedPage wrt rdr (inc i))]
            (.addPage wrt page)))))
    (.flush out)
    (.close doc)
    (.close out)))

;;;templating
(defmacro template [t]
  `(fn [~'items]
     (for [~'item ~'items]
       ~(clojure.walk/prewalk
          (fn [x#]
            (if (and (symbol? x#) (.startsWith (name x#) "$"))
              `(~(keyword (.substring (name x#) 1)) ~'item)
              x#))
          t))))
