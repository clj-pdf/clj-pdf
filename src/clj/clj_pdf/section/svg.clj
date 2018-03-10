(ns clj-pdf.section.svg
  (:require [clj-pdf.section :refer [render *cache*]]
            [clj-pdf.graphics-2d :as g2d])
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


(defn- -render [meta svg-data]
  (let [factory  (SAXSVGDocumentFactory. (XMLResourceDescriptor/getXMLParserClassName))
        ^String uri nil
        document (.createSVGDocument factory uri (get-content svg-data))
        gfx-node (.build (GVTBuilder.) (make-ctx) document)]
    (g2d/with-graphics meta #(.paint gfx-node %))))


(defmethod render :svg [_ meta svg-data]
  (let [svg-hash (.hashCode [meta svg-data])]
    (if-let [cached (get *cache* svg-hash)]
      cached
      (let [compiled (-render meta svg-data)]
        (swap! *cache* assoc svg-hash compiled)
        compiled))))
