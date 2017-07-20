(ns clj-pdf.core
  (:require
    [clojure.java.io :as io]
    [clojure.walk :refer :all]
    [clojure.set :refer [rename-keys]]
    [clojure.string :refer [split]]
    [clj-pdf.charting :as charting]
    [clj-pdf.svg :as svg]
    [clj-pdf.graphics-2d :as g2d])
  (:import
    [java.awt Color Graphics2D Toolkit Canvas]
    [java.awt.image BufferedImage]
    [java.io PushbackReader InputStream InputStreamReader OutputStream FileOutputStream ByteArrayOutputStream File]
    [java.net URL URI]
    [javax.imageio ImageIO]
    [org.apache.commons.codec.binary Base64]
    [cljpdf.text
     Anchor
     Annotation
     Cell
     ChapterAutoNumber
     Chunk
     Document
     Element
     Font
     FontFactory
     GreekList
     HeaderFooter
     Image
     ImgRaw
     List
     ListItem
     PageSize
     Paragraph
     Phrase
     Rectangle
     RectangleReadOnly
     RomanList
     Section
     Table
     ZapfDingbatsList
     ZapfDingbatsNumberList]
    [cljpdf.text.pdf.draw DottedLineSeparator LineSeparator]
    [cljpdf.text.pdf BaseFont MultiColumnText PdfContentByte PdfReader PdfStamper PdfWriter PdfPageEventHelper PdfPCell PdfPTable]))

(declare ^:dynamic *cache*)
(def fonts-registered? (atom nil))

(declare make-section)

(defn- styled-item [meta item]
  (make-section meta (if (string? item) [:chunk item] item)))

(defn- pdf-styled-item [meta item]
  (make-section meta (if (string? item) [:phrase item] item)))

(defn- get-alignment [align]
  (condp = (when align (name align))
    "left" Element/ALIGN_LEFT
    "center" Element/ALIGN_CENTER
    "right" Element/ALIGN_RIGHT
    "justified" Element/ALIGN_JUSTIFIED
    "top" Element/ALIGN_TOP
    "middle" Element/ALIGN_MIDDLE
    "bottom" Element/ALIGN_BOTTOM
    Element/ALIGN_LEFT))

(defn- set-background [^Chunk element {:keys [background]}]
  (when background
   (let [[r g b] background] (.setBackground element (Color. (int r) (int g) (int b))))))

(defn get-style [style]
  (condp = (when style (name style))
    "bold" Font/BOLD
    "italic" Font/ITALIC
    "bold-italic" Font/BOLDITALIC
    "normal" Font/NORMAL
    "strikethru" Font/STRIKETHRU
    "underline" Font/UNDERLINE
    Font/NORMAL))

(defn- compute-font-style [styles]
  (if (> (count styles) 1)
    (apply bit-or (map get-style styles))
    (get-style (first styles))))

(defn- font
  ^Font
  [{style    :style
    styles   :styles
    size     :size
    [r g b]  :color
    family   :family
    ttf-name :ttf-name
    encoding :encoding}]
  (FontFactory/getFont
    (if-not (nil? ttf-name)
      ttf-name
      (condp = (when family (name family))
        "courier" FontFactory/COURIER
        "helvetica" FontFactory/HELVETICA
        "times-roman" FontFactory/TIMES_ROMAN
        "symbol" FontFactory/SYMBOL
        "zapfdingbats" FontFactory/ZAPFDINGBATS
        FontFactory/HELVETICA))

    (case [(not (nil? ttf-name))
           (if (keyword? encoding) encoding :custom)]
      [true :unicode] BaseFont/IDENTITY_H
      [true :custom] (or encoding BaseFont/IDENTITY_H)
      [true :default] BaseFont/WINANSI
      BaseFont/WINANSI)

    true

    (float (or size 10))
    (cond
      styles (compute-font-style styles)
      style (get-style style)
      :else Font/NORMAL)

    (if (and r g b)
      (new Color (int r) (int g) (int b))
      (new Color (int 0) (int 0) (int 0)))))

(defn- custom-page-size [width height]
  (RectangleReadOnly. width height))

(defn- page-size [size]
  (if (vector? size)
    (apply custom-page-size size)
    (condp = (when size (name size))
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


(defn- chapter [meta & [title & sections]]
  (let [ch (new ChapterAutoNumber
                (make-section meta (if (string? title) [:paragraph title] title)))]
    (doseq [section sections]
      (make-section (assoc meta :parent ch) section))
    ch))


(defn- heading [meta & content]
  (make-section
    (into [:paragraph (merge meta (merge {:size 18 :style :bold} (:style meta)))] content)))


(defn- paragraph [{:keys [first-line-indent indent indent-left indent-right spacing-before spacing-after keep-together leading align ] :as meta}
                  & content]
  (let [paragraph (Paragraph.)
        indent (or indent indent-left)]
    (if leading (.setLeading paragraph leading) (.setLeading paragraph 0 1.5))
    (.setFont paragraph (font meta))
    (if keep-together (.setKeepTogether paragraph true))
    (if first-line-indent (.setFirstLineIndent paragraph (float first-line-indent)))
    (if indent (.setIndentationLeft paragraph (float indent)))
    (if indent-right (.setIndentationRight paragraph (float indent-right)))
    (if spacing-before (.setSpacingBefore paragraph (float spacing-before)))
    (if spacing-after (.setSpacingAfter paragraph (float spacing-after)))
    (if align (.setAlignment paragraph ^int (get-alignment align)))

    (doseq [item content]
      (.add paragraph
            (make-section
              meta
              (if (string? item) [:chunk item] item))))

    paragraph))


(defn- li [{:keys [numbered
                   lettered
                   roman
                   greek
                   dingbats
                   dingbats-char-num
                   dingbatsnumber
                   dingbatsnumber-type
                   lowercase
                   indent
                   symbol] :as meta}
           & items]
  (let [list
        (cond
          ^RomanList roman (new RomanList)
          ^GreekList greek (new GreekList)
          ^ZapfDingbatsList dingbats (new ZapfDingbatsList dingbats-char-num)
          ^ZapDingbatsNumberList dingbatsnumber (new ZapfDingbatsNumberList dingbatsnumber-type)
          :else (^List new List (boolean (or numbered false)) (boolean (or lettered false))))]

    (if lowercase (.setLowercase list lowercase))
    (if indent (.setIndentationLeft list (float indent)))
    (if symbol (.setListSymbol list (str symbol)))

    (doseq [item items]
      (.add list (new ListItem (styled-item meta item))))
    list))


(defn- phrase
  [{:keys [leading] :as meta} & content]
  (doto (if leading (new Phrase (float leading)) (new Phrase))
    (.setFont (font meta))
    (.addAll (map (partial make-section meta) content))))

(defn- image-chunk [meta ^Image image]
  (new Chunk
       image
       (float (or (:x meta) 0))
       (float (or (:y meta) 0))))

(defn- text-chunk [style content]
  (let [ch (new Chunk ^String (make-section content) ^Font (font style))]
    (set-background ch style)
    (cond
      (:super style) (.setTextRise ch (float 5))
      (:sub style) (.setTextRise ch (float -4))
      :else ch)))

(defn- make-chunk [meta content]
  (let [children (make-section content)]
    (if (instance? ImgRaw children)
      (image-chunk meta children)
      (text-chunk meta children))))

(defn- annotation
  ([_ title text] (annotation title text))
  ([title text] (new Annotation title text)))


(defn- anchor [{:keys [style leading id target] :as meta} content]
  (let [a (cond (and style leading) (new Anchor (float leading) content (font style))
                leading (new Anchor (float leading) (styled-item meta content))
                style (new Anchor ^String content (font style))
                :else (new Anchor (styled-item meta content)))]
    (if id (.setName a id))
    (if target (.setReference a target))
    a))


(defn- get-border [borders]
  (reduce +
          (vals
            (select-keys
              {:top Cell/TOP :bottom Cell/BOTTOM :left Cell/LEFT :right Cell/RIGHT}
              borders))))

(defn- cell [{:keys [background-color
                     colspan
                     rowspan
                     border
                     align
                     valign
                     leading
                     set-border
                     border-color
                     border-width
                     border-width-bottom
                     border-width-left
                     border-width-right
                     border-width-top] :as meta}
             & content]

  (let [c (Cell.)
        [r g b] background-color]

    (if (and r g b) (.setBackgroundColor c (Color. (int r) (int g) (int b))))
    (when (not (nil? border))
      (.setBorder c (if border Rectangle/BOX Rectangle/NO_BORDER)))

    (if rowspan (.setRowspan c (int rowspan)))
    (if colspan (.setColspan c (int colspan)))
    (if set-border (.setBorder c (int (get-border set-border))))
    (if border-width (.setBorderWidth c (float border-width)))
    (if border-width-bottom (.setBorderWidthBottom c (float border-width-bottom)))
    (if border-width-left (.setBorderWidthLeft c (float border-width-left)))
    (if border-width-right (.setBorderWidthRight c (float border-width-right)))
    (if border-width-top (.setBorderWidthTop c (float border-width-top)))
    (if valign (.setVerticalAlignment c ^int (get-alignment valign)))
    (.setHorizontalAlignment c ^int (get-alignment align))
    (if leading (.setLeading c (float leading)))

    (doseq [item (map
                   #(make-section meta (if (string? %) [:chunk %] %))
                   content)]
      (.addElement c item))
    c))


(defn- pdf-cell-padding*
  ([^PdfPCell cell a] (.setPadding cell (float a)))
  ([^PdfPCell cell a b] (pdf-cell-padding* cell a b a b))
  ([^PdfPCell cell a b c] (pdf-cell-padding* cell a b c b))
  ([^PdfPCell cell a b c d]
   (doto cell
     (.setPaddingTop (float a))
     (.setPaddingRight (float b))
     (.setPaddingBottom (float c))
     (.setPaddingLeft (float d)))))

(defn- pdf-cell-padding [^PdfPCell cell pad]
  (when-let [args (if (sequential? pad) pad [pad])]
    (apply pdf-cell-padding* cell args)))

(defn- pdf-cell [{:keys [background-color
                         colspan
                         rowspan
                         border
                         align
                         valign
                         set-border
                         border-color
                         border-width
                         border-width-bottom
                         border-width-left
                         border-width-right
                         border-width-top
                         padding
                         padding-bottom
                         padding-left
                         padding-right
                         padding-top
                         rotation
                         height
                         min-height] :as meta}
                 & content]
  (let [c (PdfPCell.)]

    (let [[r g b] background-color]
      (if (and r g b) (.setBackgroundColor c (Color. (int r) (int g) (int b)))))

    (let [[r g b] border-color]
      (if (and r g b) (.setBorderColor c (Color. (int r) (int g) (int b)))))

    (when (not (nil? border))
      (.setBorder c (if border Rectangle/BOX Rectangle/NO_BORDER)))

    (if rowspan (.setRowspan c (int rowspan)))
    (if colspan (.setColspan c (int colspan)))
    (if set-border (.setBorder c (int (get-border set-border))))
    (if border-width (.setBorderWidth c (float border-width)))
    (if border-width-bottom (.setBorderWidthBottom c (float border-width-bottom)))
    (if border-width-left (.setBorderWidthLeft c (float border-width-left)))
    (if border-width-right (.setBorderWidthRight c (float border-width-right)))
    (if border-width-top (.setBorderWidthTop c (float border-width-top)))
    (if padding (pdf-cell-padding c padding))
    (if padding-bottom (.setPaddingBottom c (float padding-bottom)))
    (if padding-left (.setPaddingLeft c (float padding-left)))
    (if padding-right (.setPaddingRight c (float padding-right)))
    (if padding-top (.setPaddingTop c (float padding-top)))
    (if rotation (.setRotation c (int rotation)))
    (if height (.setFixedHeight c (float height)))
    (if min-height (.setMinimumHeight c (float min-height)))
    (.setHorizontalAlignment c ^int (get-alignment align))
    (.setVerticalAlignment c ^int (get-alignment valign))
    (doseq [item (map
                   #(make-section meta (if (string? %) [:paragraph %] %))
                   content)]
      (.addElement c item))
    c))


(defn- table-header [meta ^Table tbl header cols]
  (when header
    (let [meta?       (map? (first header))
          header-rest (if meta? (rest header) header)
          header-data header-rest
          set-bg      #(if-let [[r g b] (if meta? (:background-color (first header)))]
                         (doto ^Cell % (.setBackgroundColor (new Color (int r) (int g) (int b)))) %)]
      (if (= 1 (count header-data))
        (let [header               (first header-data)
              ^Element header-text (if (string? header)
                                     (make-section meta [:phrase {:style "bold"} header])
                                     (make-section meta header))
              header-cell          (doto (new Cell header-text)
                                     (.setHorizontalAlignment 1)
                                     (.setHeader true)
                                     (.setColspan cols))]
          (set-bg header-cell)
          (.addCell tbl header-cell))

        (doseq [h header-data]
          (let [^Element header-text (if (string? h)
                                       (make-section meta [:phrase {:style "bold"} h])
                                       (make-section meta h))
                ^Cell header-cell    (if (= Cell (type header-text))
                                       header-text
                                       (new Cell header-text))
                header-cell          (doto header-cell (.setHeader true))]
            (when-not (and (string? h)
                           (map? (second h)))
              (when-let [align (:align (second h))]
                (.setHorizontalAlignment header-cell ^int (get-alignment align))))
            (set-bg header-cell)
            (.addCell tbl header-cell)))))
    (.endHeaders tbl)))

(declare split-classes-from-tag)

(defn- add-table-cell
  [^Table tbl meta content]
  (let [[tag & classes] (when (vector? content)
                          (split-classes-from-tag (first content)))
        element (cond
                  (= tag :cell) content
                  (nil? content) [:cell [:chunk meta ""]]
                  (string? content) [:cell [:chunk meta content]]
                  :else [:cell content])]
    (.addCell tbl ^Cell (make-section meta element))))

(defn- table [{:keys [align
                      background-color
                      border
                      border-width
                      cell-border
                      header
                      no-split-cells?
                      num-cols
                      offset
                      padding
                      spacing
                      width
                      widths]
               :as   meta}
              & rows]
  (when (< (count rows) 1) (throw (new Exception "Table must contain rows!")))

  (let [header-cols (cond-> (count header)
                            (map? (first header)) dec)
        cols        (or num-cols (apply max (cons header-cols (map count rows))))
        ^Table tbl  (doto (new Table cols (count rows)) (.setWidth (float (or width 100))))]

    (when widths
      (if (= (count widths) cols)
        (.setWidths tbl (int-array widths))
        (throw (new Exception (str "wrong number of columns specified in widths: " widths ", number of columns: " cols)))))

    (if (= false border)
      (.setBorder tbl Rectangle/NO_BORDER)
      (when border-width (.setBorderWidth tbl (float border-width))))

    (when (= false cell-border)
      (.setDefaultCell tbl (doto (new Cell) (.setBorder Rectangle/NO_BORDER))))

    (if background-color (let [[r g b] background-color] (.setBackgroundColor tbl (new Color (int r) (int g) (int b)))))
    (.setPadding tbl (if padding (float padding) (float 3)))
    (if spacing (.setSpacing tbl (float spacing)))
    (if offset (.setOffset tbl (float offset)))
    (table-header meta tbl header cols)

    (.setAlignment tbl ^int (get-alignment align))

    (.setCellsFitPage tbl (boolean no-split-cells?))

    (doseq [row rows]
      (doseq [column row]
        (add-table-cell tbl (dissoc meta :header :align :offset :num-cols :width :widths) column)))

    tbl))

(defn- add-pdf-table-cell
  [^PdfPTable tbl meta content]
  (let [[tag & classes] (when (vector? content)
                          (split-classes-from-tag (first content)))
        element (cond
                  (= tag :pdf-cell) content
                  (nil? content) [:pdf-cell [:chunk meta ""]]
                  (string? content) [:pdf-cell [:chunk meta content]]
                  :else [:pdf-cell content])]
    (.addCell tbl ^PdfPCell (make-section meta element))))

(defn- pdf-table [{:keys [bounding-box
                          cell-border
                          footer
                          header
                          horizontal-align
                          keep-together?
                          no-split-rows?
                          no-split-late?
                          num-cols
                          spacing-after
                          spacing-before
                          table-events
                          width
                          width-percent]
                   :as   meta}
                  widths
                  & rows]
  (when (empty? rows)
    (throw (new Exception "Table must contain at least one row")))
  (let [header-size (if (seq header) (count header))
        footer-size (if (seq footer) (count footer))
        ; with PdfPTable, the header and footer rows need to go first in the list
        ; of table rows provided to it
        rows        (concat (if header-size header) (if footer-size footer) rows)]
    (when (and widths
               (not= (count widths)
                     (or num-cols (apply max (map count rows)))))
      (throw (new Exception (str "wrong number of columns specified in widths: " widths ", number of columns: " (or num-cols (apply max (map count rows)))))))

    (let [^int cols (or num-cols (apply max (map count rows)))
          tbl       (new PdfPTable cols)]

      ; PdfPTable is pretty weird. setHeaderRows needs to be given a number that is
      ; the sum of the total number of header _and_ footer rows that this table
      ; will have, while setFooterRows is just the number of footer rows.
      (if (or header-size footer-size)
        (.setHeaderRows tbl (int (+ (or header-size 0) (or footer-size 0)))))
      (if footer-size (.setFooterRows tbl (int footer-size)))

      (when width (.setTotalWidth tbl (float width)))
      (when width-percent (.setWidthPercentage tbl (float width-percent)))

      (if bounding-box
        (if-not widths
          (throw (new Exception "widths must be non-nil when bounding-box is used in a pdf-table"))
          (let [[x y] bounding-box]
            (.setWidthPercentage tbl (float-array widths) (make-section [:rectangle x y]))))
        (if widths
          (.setWidths tbl (float-array widths))))

      (doseq [table-event table-events]
        (.setTableEvent tbl table-event))

      (if spacing-before (.setSpacingBefore tbl (float spacing-before)))
      (if spacing-after (.setSpacingAfter tbl (float spacing-after)))

      (.setHorizontalAlignment tbl ^int (get-alignment horizontal-align))

      (.setKeepTogether tbl (boolean keep-together?))

      ; these are inverted so the default if not specified matches
      ; PdfPTable default behaviour
      (.setSplitRows tbl (boolean (not no-split-rows?)))
      (.setSplitLate tbl (boolean (not no-split-late?)))

      (doseq [row rows]
        (doseq [column row]
          (add-pdf-table-cell tbl (merge meta (when (= false cell-border) {:set-border []})) column)))

      tbl)))

(defn load-image [img-data base64]
  (cond
    (instance? java.awt.Image img-data)
    (Image/getInstance (.createImage (Toolkit/getDefaultToolkit) (.getSource ^java.awt.Image img-data)) nil)

    base64
    (Image/getInstance (.createImage (Toolkit/getDefaultToolkit) (^bytes Base64/decodeBase64 ^String img-data)) nil)

    (= Byte/TYPE (.getComponentType (class img-data)))
    (Image/getInstance (.createImage (Toolkit/getDefaultToolkit) ^bytes img-data) nil)

    (string? img-data)
    (Image/getInstance ^String img-data)

    (instance? URL img-data)
    (Image/getInstance ^URL img-data)

    :else
    (throw (new Exception (str "Unsupported image data: " img-data ", must be one of java.net.URL, java.awt.Image, or filename string")))))

(defn- make-image [{:keys [scale
                           xscale
                           yscale
                           align
                           width
                           height
                           base64
                           rotation
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
  (let [^Image img (load-image img-data base64)
        img-width  (.getWidth img)
        img-height (.getHeight img)]
    (if rotation (.setRotation img (float rotation)))
    (if align (.setAlignment img ^int (get-alignment align)))
    (if annotation (let [[title text] annotation] (.setAnnotation img (make-section [:annotation title text]))))
    (if pad-left (.setIndentationLeft img (float pad-left)))
    (if pad-right (.setIndentationRight img (float pad-right)))

    ;;scale relative to page size
    (if (and page-width page-height left-margin right-margin top-margin bottom-margin)
      (let [available-width  (- page-width (+ left-margin right-margin))
            available-height (- page-height (+ top-margin bottom-margin))
            page-scale       (* 100
                                (cond
                                  (and (> img-width available-width)
                                       (> img-height available-height))
                                  (if (> img-width img-height)
                                    (/ available-width img-width)
                                    (/ available-height img-height))

                                  (> img-width available-width)
                                  (/ available-width img-width)

                                  (> img-height available-height)
                                  (/ available-height img-height)

                                  :else 1))]
        (cond
          (and xscale yscale) (.scalePercent img (float (* page-scale xscale)) (float (* page-scale yscale)))
          xscale (.scalePercent img (float (* page-scale xscale)) (float 100))
          yscale (.scalePercent img (float 100) (float (* page-scale yscale)))
          :else (when (or (> img-width available-width) (> img-height available-height))
                  (.scalePercent img (float page-scale))))))

    (if width (.scaleAbsoluteWidth img (float width)))
    (if height (.scaleAbsoluteHeight img (float height)))
    (if scale (.scalePercent img scale))
    img))

(defn- image [& [meta img-data :as params]]
  (let [image-hash (.hashCode params)]
    (if-let [cached (get @*cache* image-hash)]
      cached
      (let [compiled (make-image meta img-data)]
        (swap! *cache* assoc image-hash compiled)
        compiled))))

(defn- section [meta & [title & content]]
  (let [paragraph (make-section meta (if (string? title) [:paragraph title] title))
        sec       (.addSection ^Section (:parent meta) ^Paragraph paragraph)
        indent    (:indent meta)]
    (if indent (.setIndentation sec (float indent)))
    (doseq [item content]
      (if (and (coll? item) (= "section" (name (first item))))
        (make-section (assoc meta :parent sec) item)
        (.add sec (make-section meta (if (string? item) [:chunk item] item)))))))


(defn- subscript [meta text]
  (text-chunk (assoc meta :sub true) text))


(defn- superscript [meta text]
  (text-chunk (assoc meta :super true) text))

(defn- make-chart [& [meta & more :as params]]
  (let [{:keys [vector align width height page-width page-height]} meta]
    (if vector
      (apply charting/chart params)
      (image
        (cond
          (and align width height) meta
          (and width height) (assoc meta :align :center)
          align (assoc meta :width (* 0.85 page-width) :height (* 0.85 page-height))
          :else (assoc meta
                  :align :center
                  :width (* 0.85 page-width)
                  :height (* 0.85 page-height)))
        (apply charting/chart params)))))

(defn- chart [& params]
  (let [chart-hash (.hashCode params)]
    (if-let [cached (get @*cache* chart-hash)]
      cached
      (let [compiled (apply make-chart params)]
        (swap! *cache* assoc chart-hash compiled)
        compiled))))

(defn- svg-element [& params]
  (let [svg-hash (.hashCode params)]
    (if-let [cached (get *cache* svg-hash)]
      cached
      (let [compiled (apply svg/render params)]
        (swap! *cache* assoc svg-hash compiled)
        compiled))))

(defn- line [{dotted? :dotted, gap :gap, [r g b] :color width :line-width} & _]
  (let [^LineSeparator lineSeparator (if dotted?
                                       (if gap
                                         (doto (new DottedLineSeparator) (.setGap (float gap)))
                                         (new DottedLineSeparator))
                                       (new LineSeparator))]
    (if (and r g b) (.setLineColor lineSeparator (new Color ^int r ^int g ^int b)))
    (if width (.setLineWidth lineSeparator (float width)))
    (.setOffset lineSeparator -5)
    lineSeparator))

(defn- reference [meta reference-id]
  (if-let [item (get @*cache* reference-id)]
    item
    (if-let [item (get-in meta [:references reference-id])]
      (let [item (make-section item)]
        (swap! *cache* assoc reference-id item)
        item)
      (throw (Exception. (str "reference tag not found: " reference-id))))))

(defn- multi-column [{:keys [left-margin right-margin page-width gutter-width top height columns] :as meta} content]
  (let [ml-text (cond
                  (and top height)
                  (MultiColumnText. (float top) (float height))
                  height
                  (MultiColumnText. (float height))
                  :else
                  (MultiColumnText. MultiColumnText/AUTOMATIC))]
    (.addRegularColumns ml-text
                        (float left-margin)
                        (float (- page-width right-margin))
                        (float (or gutter-width 10))
                        (int columns))
    (.addElement ml-text (make-section meta (if (string? content)
                                              [:phrase content]
                                              content)))
    ml-text))

(defn- spacer
  ([meta] (spacer meta 1))
  ([meta height]
   (make-section [:paragraph (merge {:leading (:size meta 12)} meta) (apply str (take height (repeat "\n")))])))

(defn- rectangle
  [_ width height]
  (new Rectangle width height))

(defn- split-classes-from-tag
  [tag]
  (map keyword (split (name tag) #"\.")))

(defn- get-class-attributes
  [stylesheet classes]
  (apply merge (map stylesheet classes)))

(defn- make-section
  ([element]
   (cond
     (empty? element)
     ""
     (every? sequential? element)
     (doseq [item element]
       (make-section item))
     element
     (make-section {} element)
     :else
     ""))
  ([meta element]
   (try
     (cond
       (string? element) element
       (nil? element) ""
       (number? element) (str element)
       :else
       (let [[element-name & [h & t :as content]] element
             tag         (if (string? element-name) (keyword element-name) element-name)
             [tag & classes] (split-classes-from-tag tag)
             class-attrs (get-class-attributes (:stylesheet meta) classes)
             new-meta    (cond-> meta
                                 class-attrs (merge class-attrs)
                                 (map? h) (merge h))
             elements    (if (map? h) t content)]

         (apply
           (condp = tag
             :anchor anchor
             :annotation annotation
             :cell cell
             :pdf-cell pdf-cell
             :chapter chapter
             :chart chart
             :chunk make-chunk
             :heading heading
             :image image
             :graphics g2d/with-graphics
             :svg svg-element
             :line line
             :list li
             :multi-column multi-column
             :paragraph paragraph
             :phrase phrase
             :reference reference
             :rectangle rectangle
             :section section
             :spacer spacer
             :superscript superscript
             :subscript subscript
             :table table
             :pdf-table pdf-table
             (throw (ex-info (str "invalid tag: " tag) {:element element})))
           (cons new-meta elements))))
     (catch Exception e
       (throw (ex-info "failed to parse element" {:meta meta :element element} e))))))

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
    (.setHeader doc (doto (new HeaderFooter (new Phrase ^String header ^Font (font font-style)) false) (.setBorderWidthTop 0)))))

(defn set-header-footer-table-width [table ^Document doc page-numbers?]
  (let [default-width (- (.right doc) (.left doc) (if page-numbers? 20 0))]
    (if (map? (second table))
      (update-in table [1 :width] #(or % default-width))
      (concat [(first table)] [{:width default-width}] (rest table)))))

(defn- get-header-footer-table-section [table-content meta ^Document doc page-numbers? footer?]
  (as-> table-content x
        (set-header-footer-table-width x doc (and footer? page-numbers?))
        ; :header and :footer are different for the root document meta map vs. the meta map
        ; that is expected for :pdf-table (which is what 'x' here should be at this point)
        ; TODO: remove other possible map key conflicts? i think these are the only 2 ...
        (make-section (dissoc meta :header :footer) x)))

(defn table-header-footer-height [content meta ^Document doc page-numbers? footer?]
  (let [table        (get-header-footer-table-section (:table content) meta doc page-numbers? footer?)
        table-height (.getTotalHeight ^PdfPTable table)]
    table-height))

(defn set-margins [^Document doc left-margin right-margin top-margin bottom-margin page-numbers?]
  (let [margins {:left   (or left-margin (.leftMargin doc))
                 :right  (or right-margin (.rightMargin doc))
                 :top    (or top-margin (.topMargin doc))
                 :bottom (+ (if page-numbers? 20 0)
                            (or bottom-margin (.bottomMargin doc)))}]
    (.setMargins doc (float (:left margins)) (float (:right margins)) (float (:top margins)) (float (:bottom margins)))
    margins))

(defn write-header-footer-content-row [{:keys [table x y] :as content} ^PdfWriter writer]
  (.writeSelectedRows ^PdfPTable table (int 0) (int -1) (float x) (float y) (.getDirectContent writer)))

(defn table-footer-header-event [header-content footer-content margins header-first-page?]
  (proxy [PdfPageEventHelper] []
    (onBeforeStartPage [^PdfWriter writer ^Document doc]
      ; sets the margins for the current page
      ; typically, the only thing that would make it so that the margins change on a per-page basis
      ; is if both a letterhead and header are used. in this case, the first page will have a different
      ; top-margin then the rest of the document.
      (let [page-num    (.getPageNumber doc)
            first-page? (= page-num 1)
            has-header? (and header-content (or (not first-page?) header-first-page?))
            has-footer? (boolean footer-content)
            left        (:left margins)
            right       (:right margins)
            top         (if has-header?
                          (+ (:top margins) (.getTotalHeight ^PdfPTable (:table header-content)))
                          (:top margins))
            bottom      (if has-footer?
                          (+ (:bottom margins) (.getTotalHeight ^PdfPTable (:table footer-content)))
                          (:bottom margins))]
        (.setMargins doc (float left) (float right) (float top) (float bottom))))

    (onEndPage [^PdfWriter writer ^Document doc]
      (let [page-num     (.getPageNumber doc)
            first-page?  (= page-num 1)
            show-header? (and header-content (or (not first-page?) header-first-page?))
            show-footer? (boolean footer-content)]
        ; write header and/or footer tables to appropriate places on the page if they are set and required by the
        ; current state (e.g. use of a letterhead when on page #1 will mean no header even if one is set)
        (if show-header? (write-header-footer-content-row header-content writer))
        (if show-footer? (write-header-footer-content-row footer-content writer))))))

(defn preprocess-header-footer-content [content meta ^Document doc footer? page-numbers? header-first-page?]
  (let [table  (get-header-footer-table-section (:table content) meta doc page-numbers? footer?)
        height (.getTotalHeight ^PdfPTable table)
        y      (if footer?
                 (+ (.bottom doc) height)
                 (.top doc))]
    (-> content
        (assoc-in [:table] table)
        (update-in [:x] #(or % (.left doc)))
        (update-in [:y] #(or % y)))))

(defn set-table-header-footer-event [header-content footer-content meta doc margins page-numbers? ^PdfWriter pdf-writer header-first-page?]
  (let [header-content (if header-content (preprocess-header-footer-content header-content meta doc false page-numbers? header-first-page?))
        footer-content (if footer-content (preprocess-header-footer-content footer-content meta doc true page-numbers? header-first-page?))]
    (.setPageEvent pdf-writer (table-footer-header-event header-content footer-content margins header-first-page?))))

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
        (let [{:keys [render scale rotate translate]} (:watermark meta)]
          (g2d/with-graphics
            (assoc meta
              :pdf-writer writer
              :under true
              :scale scale
              :rotate rotate
              :translate translate)
            (or render
                (fn [^Graphics2D g2d]
                  (.drawImage
                    g2d
                    ^BufferedImage image
                    nil
                    (int 0)
                    (int 0))))))))))

(defn background-color-applier [background-color]
  (proxy [PdfPageEventHelper] []
    (onEndPage [^PdfWriter writer ^Document doc]
      (let [^PdfContentByte canvas (.getDirectContentUnder writer)
            ^Rectangle rect (.getPageSize doc)
            [r g b] background-color
            ]
        (.setColorFill canvas (new Color ^int r ^int g ^int b))
        (.rectangle canvas (.getLeft rect) (.getBottom rect) (.getWidth rect) (.getHeight rect))
        (.fill canvas)))))

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
        header-first-page? (if letterhead false true)]

    ;;header and footer must be set before the doc is opened, or itext will not put them on the first page!
    ;;if we have to print total pages or add a watermark, then the document has to be post processed
    (let [output-stream-to-use (if (page-events? meta) temp-stream output-stream)
          pdf-writer           (PdfWriter/getInstance doc output-stream-to-use)
          header-meta          (merge font-style (dissoc meta :size))
          margins              (set-margins doc left-margin right-margin top-margin bottom-margin page-numbers?)]

      (set-table-header-footer-event table-header table-footer header-meta doc margins page-numbers? pdf-writer header-first-page?)

      (if background-color
        (.setPageEvent pdf-writer (background-color-applier background-color)))

      (if watermark
        (.setPageEvent pdf-writer (watermark-stamper (assoc meta
                                                       :page-width width
                                                       :page-height height))))

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
      (if (and left-margin right-margin top-margin bottom-margin)
        (.setMargins doc
                     (float left-margin)
                     (float right-margin)
                     (float top-margin)
                     (float (if pages (+ 20 bottom-margin) bottom-margin))))


      ;;if we have a letterhead then we want to put it on the first page instead of the header,
      ;;so we will open doc beofore adding the header
      (if letterhead
        (do
          (.open doc)
          (doseq [item letterhead]
            (append-to-doc nil nil font-style width height (if (string? item) [:paragraph item] item) doc pdf-writer))
          (add-header header doc font-style))
        (do
          (add-header header doc font-style)
          (.open doc)))

      (if title (.addTitle doc title))
      (if subject (.addSubject doc subject))
      (if (and nom head) (.addHeader doc nom head))
      (if author (.addAuthor doc author))
      (if creator (.addCreator doc creator))

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
  (if (and (coll? item) (coll? (first item)))
    (doseq [element item]
      (append-to-doc stylesheet references font width height (preprocess-item element) doc pdf-writer))
    (append-to-doc stylesheet references font width height (preprocess-item item) doc pdf-writer)))

(defn- register-fonts [doc-meta]
  (when (and (= true (:register-system-fonts? doc-meta))
             (nil? @fonts-registered?))
    ; register fonts in usual directories
    (FontFactory/registerDirectories)
    (g2d/g2d-register-fonts)
    (reset! fonts-registered? true)))

(defn- parse-meta [doc-meta]
  (register-fonts doc-meta)
  ; font would conflict with a function definition
  (assoc doc-meta :font-style (:font doc-meta)))

(defn- write-doc
  "(write-doc document out)
  document consists of a vector containing a map which defines the document metadata and the contents of the document
  out can either be a string which will be treated as a filename or an output stream"
  [[doc-meta & content] out]
  (let [doc-meta (-> doc-meta
                     parse-meta
                     (assoc :total-pages (:pages doc-meta))
                     (assoc :pages (boolean (or (:pages doc-meta) (-> doc-meta :footer :table)))))
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
    (doseq [item (rest items)]
      (add-item item doc-meta width height doc pdf-writer))
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
  "usage:
   in can be either a vector containing the document or an input stream. If in is an input stream then the forms will be read sequentially from it.
   out can be either a string, in which case it's treated as a file name, or an output stream.
   NOTE: using the :pages option will cause the complete document to reside in memory as it will need to be post processed."
  [in out]
  (binding [*cache* (atom {})]
    (cond (instance? InputStream in) (stream-doc in out)
          (seq? in) (seq-to-doc in out)
          :else (write-doc in out))))

(defn collate
  "usage: takes an output that can be a file name or an output stream followed by one or more documents
   that can be input streams, urls, filenames, or byte arrays."
  [& params]
  (let [[{:keys [author creator orientation size subject title]} out & pdfs]
        (if (map? (first params))
          params
          (into [{}] params))
        out (io/output-stream out)
        doc (Document. (page-orientation (page-size size) orientation))
        wrt (PdfWriter/getInstance doc out)
        cb  ^PdfContentByte (do (.open doc) (.getDirectContent wrt))]
    (when title (.addTitle doc title))
    (when subject (.addSubject doc subject))
    (when author (.addAuthor doc author))
    (when creator (.addCreator doc creator))
    (doseq [pdf pdfs]
      (with-open [rdr (PdfReader. (io/input-stream pdf))]
        (dotimes [i (.getNumberOfPages rdr)]
          (.newPage doc)
          (.addTemplate cb (.getImportedPage wrt rdr (inc i)) 0 0))))
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
