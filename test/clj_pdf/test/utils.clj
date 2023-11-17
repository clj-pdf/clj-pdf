(ns clj-pdf.test.utils
  (:require [clojure.test :refer [run-tests is]]
            [clj-pdf.core :refer [pdf]]
            [clojure.string :as str]
            [clojure.java.io :as io]))

(defn add-test-path-prefix [filename]
  (str "test" java.io.File/separator filename))

(defn fix-pdf [^bytes pdf-bytes]
  (-> (String. pdf-bytes)
      ; obviously these will get changed on each run, so strip the creation/modification date/times
      (str/replace #"ModDate\((.*?)\)" "")
      (str/replace #"CreationDate\((.*?)\)" "")
      ; not sure what this is for ... ?
      (str/replace #"\[<(.*?)>\]" "")
      ; these are kind of hacky, but it seems that the prefix characters before the font name "Carlito"
      ; will get randomly generated on each run ... ?
      (str/replace #"Font\/([A-Z]+\+Carlito)" "Font/PHZPHX+Carlito")
      (str/replace #"FontBBox\/([A-Z]+\+Carlito)" "FontBBox/PHZPHX+Carlito")
      (str/replace #"FontName\/([A-Z]+\+Carlito)" "FontName/PHZPHX+Carlito")
      (str/replace #"BaseFont\/([A-Z]+\+Carlito)" "BaseFont/PHZPHX+Carlito")

      (str/replace #"Font\/([A-Z]+\+CourierPrime)"
                   "Font/BUKDVX+CourierPrime")
      (str/replace #"FontBBox\/([A-Z]+\+CourierPrime)"
                   "FontBBox/BUKDVX+CourierPrime")
      (str/replace #"FontName\/([A-Z]+\+CourierPrime)"
                   "FontName/BUKDVX+CourierPrime")
      (str/replace #"BaseFont\/([A-Z]+\+CourierPrime)"
                   "BaseFont/BUKDVX+CourierPrime")

      ; this number may differ on different machines
      (str/replace #"startxref\n\d*\n%%EOF\n$" "startxref\n\n%%EOF\n")))

(defn read-file ^bytes [file-path]
  (with-open [reader (io/input-stream file-path)]
    (let [length (.length (io/file file-path))
          buffer (byte-array length)]
      (.read reader buffer 0 length)
      buffer)))

(defn pdf->bytes ^bytes [doc]
  (let [out (new java.io.ByteArrayOutputStream)]
    (pdf doc out)
    (.toByteArray out)))

(def font-filename (add-test-path-prefix "Carlito-Regular.ttf"))

(defn inject-test-font [doc]
  (let [font-props    {:encoding :unicode
                       :ttf-name font-filename}]
    (update-in (vec doc) [0 :font] merge font-props)))

(defn generate-pdf [doc output-filename & _]
  (let [doc1 (inject-test-font doc)]
    (println "regenerating pdf" output-filename)
    (pdf doc1 (add-test-path-prefix output-filename))
    true))

(defn doc->stream [doc]
  (let [s (new java.io.ByteArrayOutputStream)]
    (binding [*out* (io/writer s)]
      (doseq [x doc]
        (pr x))
      (flush))
    (io/input-stream (.toByteArray s))))

(defn eq? [doc1 filename & {:keys [stream] :or {stream true}}]
  (let [filename   (add-test-path-prefix filename)
        doc1       (inject-test-font doc1)
        doc1-bytes (pdf->bytes doc1)
        seq-doc1-bytes (pdf->bytes (seq doc1))
        stream-doc1-bytes (when stream (pdf->bytes (doc->stream doc1)))
        doc2-bytes (read-file filename)]
    (is (= (fix-pdf doc1-bytes)
           (fix-pdf doc2-bytes)))
    (is (= (fix-pdf seq-doc1-bytes)
           (fix-pdf doc2-bytes))
        "seq")
    (when stream
      (is (= (fix-pdf stream-doc1-bytes)
             (fix-pdf doc2-bytes))
          "stream"))))

(defn regenerate-test-pdfs [& namespaces]
  (with-redefs [eq? generate-pdf]
    (apply run-tests namespaces)))
