(ns clj-pdf.svg
  (:use [clj-pdf.graphics-2d :only [with-graphics]])
  (:import [org.apache.batik.bridge BridgeContext DocumentLoader GVTBuilder UserAgentAdapter]
           [org.apache.batik.anim.dom SAXSVGDocumentFactory]
           [org.apache.batik.util XMLResourceDescriptor]
           [java.io Reader StringReader]))

(defn- ^BridgeContext make-ctx []
  (let [user-agent (UserAgentAdapter.)
        loader     (DocumentLoader. user-agent)
        ctx        (BridgeContext. user-agent loader)]
    (.setDynamicState ctx BridgeContext/DYNAMIC)
    ctx))

(defn- ^Reader get-content [content-or-file]
  (if (string? content-or-file)
    (StringReader. content-or-file)
    (clojure.java.io/reader content-or-file)))

(defn render [meta svg-data]
  (let [factory  (SAXSVGDocumentFactory. (XMLResourceDescriptor/getXMLParserClassName))
        ^String uri nil
        document (.createSVGDocument factory uri (get-content svg-data))
        gfx-node (.build (GVTBuilder.) (make-ctx) document)]
    (with-graphics meta #(.paint gfx-node %))))