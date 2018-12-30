(ns clj-pdf.section.core
  "File with smaller sections so they are not spread out one function at a
  separate file. Bigger sections (in character count, that is) are located in
  their own namespaces."
  (:require [clj-pdf.utils :refer [get-color get-alignment font]]
            [clj-pdf.graphics-2d :as g2d]
            [clj-pdf.section :refer [render *cache* make-section make-section-or]])
  (:import [com.lowagie.text
            Anchor Annotation ChapterAutoNumber Chunk Font ImgRaw Image
            List GreekList RomanList ListItem Paragraph Phrase Rectangle Section
            ZapfDingbatsList ZapfDingbatsNumberList]
           [com.lowagie.text.pdf MultiColumnText]
           [com.lowagie.text.pdf.draw DottedLineSeparator LineSeparator]))


(defmethod render :anchor
  [_ {:keys [style leading id target] :as meta} content]
  (let [a (cond
            (and style leading)
            (new Anchor (float leading) content (font style))

            leading
            (new Anchor (float leading) (make-section-or :chunk meta content))

            style
            (new Anchor ^String content (font style))

            :else
            (new Anchor (make-section-or :chunk meta content)))]

    (when id (.setName a id))
    (when target (.setReference a target))
    a))


(defmethod render :annotation
  ([_ _ title text] (render :annotation title text))
  ([_ title text] (new Annotation title text)))


(defmethod render :chapter
  [tag meta & [title & sections]]
  (let [ch (new ChapterAutoNumber (make-section-or :paragraph meta title))]
    (doseq [section sections]
      (make-section (assoc meta :parent ch) section))
    ch))


(defn- image-chunk [meta ^Image image]
  (new Chunk
       image
       (float (or (:x meta) 0))
       (float (or (:y meta) 0))))



(defn set-background [^Chunk element {:keys [background]}]
  (when-let [color (get-color background)]
    (.setBackground element color)))


(defn- text-chunk [style content]
  (let [ch (new Chunk ^String (make-section content) ^Font (font style))]
    (set-background ch style)
    (cond
      (:super style) (.setTextRise ch (float 5))
      (:sub style)   (.setTextRise ch (float -4))
      :else          ch)))


(defmethod render :chunk
  [_ meta content]
  (let [children (make-section content)]
    (if (instance? ImgRaw children)
      (image-chunk meta children)
      (text-chunk meta children))))


(defmethod render :graphics
  [_ meta cb]
  (g2d/with-graphics meta cb))


(defmethod render :heading
  [_ meta & content]
  (apply render :paragraph
    (merge meta (merge {:size 18 :style :bold} (:style meta)))
    content))


(defmethod render :line
  ([_ {dotted? :dotted, gap :gap, color :color width :line-width}]
   (let [^LineSeparator lineSeparator
         (if dotted?
           (if gap
             (doto (new DottedLineSeparator) (.setGap (float gap)))
             (new DottedLineSeparator))
           (new LineSeparator))]
     (when-let [c (get-color color)] (.setLineColor lineSeparator c))
     (when width (.setLineWidth lineSeparator (float width)))
     (.setOffset lineSeparator -5)
     lineSeparator))
  ([_ meta & _]
   (render :line meta)))


(defmethod render :list
  [_ {:keys [numbered
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
  (let [numbered (boolean (or numbered false))
        lettered (boolean (or lettered false))
        list
        (cond
          ^RomanList roman
          (new RomanList)

          ^GreekList greek
          (new GreekList)

          ^ZapfDingbatsList dingbats
          (new ZapfDingbatsList dingbats-char-num)

          ^ZapDingbatsNumberList dingbatsnumber
          (new ZapfDingbatsNumberList dingbatsnumber-type)

          :else
          (^List new List numbered lettered))]

    (when lowercase (.setLowercase list lowercase))
    (when indent (.setIndentationLeft list (float indent)))
    (when symbol (.setListSymbol list (str symbol)))

    (doseq [item items]
      (.add list (new ListItem (make-section-or :chunk meta item))))
    list))


(defmethod render :multi-column
  [_ {:keys [left-margin right-margin page-width gutter-width top height columns] :as meta}
   content]
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
    (.addElement ml-text (make-section-or :phrase meta content))
    ml-text))


(defmethod render :paragraph
  [_ {:keys [first-line-indent indent indent-left indent-right spacing-before spacing-after keep-together leading align ] :as meta}
   & content]

  (let [paragraph (Paragraph.)
        indent (or indent indent-left)]
    (.setFont paragraph (font meta))
    (if leading (.setLeading paragraph leading) (.setLeading paragraph 0 1.5))
    (if keep-together (.setKeepTogether paragraph true))
    (if first-line-indent (.setFirstLineIndent paragraph (float first-line-indent)))
    (if indent (.setIndentationLeft paragraph (float indent)))
    (if indent-right (.setIndentationRight paragraph (float indent-right)))
    (if spacing-before (.setSpacingBefore paragraph (float spacing-before)))
    (if spacing-after (.setSpacingAfter paragraph (float spacing-after)))
    (if align (.setAlignment paragraph ^int (get-alignment align)))

    (doseq [item content]
      (.add paragraph (make-section-or :chunk meta item)))

    paragraph))


(defmethod render :phrase
  [_ {:keys [leading] :as meta} & content]
  (doto (if leading (new Phrase (float leading)) (new Phrase))
    (.setFont (font meta))
    (.addAll (map (partial make-section meta) content))))


(defmethod render :reference
  [_ meta reference-id]
  (if-let [item (get @*cache* reference-id)]
    item
    (if-let [item (get-in meta [:references reference-id])]
      (let [item (make-section item)]
        (swap! *cache* assoc reference-id item)
        item)
      (throw (Exception. (str "reference tag not found: " reference-id))))))


(defmethod render :rectangle
  [_ _ width height]
  (new Rectangle width height))


(defmethod render :spacer
  ([_ meta] (render :spacer meta 1))
  ([_ meta height]
   (make-section [:paragraph (merge {:leading (:size meta 12)} meta) (apply str (take height (repeat "\n")))])))


(defmethod render :subscript
  [_ meta text]
  (render :chunk (assoc meta :sub true) text))


(defmethod render :superscript
  [_ meta text]
  (render :chunk (assoc meta :super true) text))


(defmethod render :section
  [_ {:keys [indent] :as meta} & [title & content]]
  (let [paragraph (make-section-or :paragraph meta title)
        sec       (.addSection ^Section (:parent meta) ^Paragraph paragraph)]
    (if indent (.setIndentation sec (float indent)))
    (doseq [item content]
      (if (and (coll? item) (= :section (first item)))
        (make-section (assoc meta :parent sec) item)
        (.add sec (make-section-or :chunk meta item))))))
