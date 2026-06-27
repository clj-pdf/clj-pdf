(ns clj-pdf.section.svg
  (:require [clj-pdf.section :refer [render *cache*]]
            [clj-pdf.graphics-2d :as g2d])
  (:import [org.apache.batik.bridge BridgeContext DocumentLoader GVTBuilder
            UserAgentAdapter EmbededExternalResourceSecurity NoLoadScriptSecurity]
           [org.apache.batik.anim.dom SAXSVGDocumentFactory]
           [org.apache.batik.util XMLResourceDescriptor]
           [java.io Reader StringReader]))


(def ^:dynamic *allow-svg-external-resources*
  "When false (the default), SVG rendering blocks Batik from loading external
   resources referenced *inside* the SVG (xlink:href on <image>/<use>, CSS, etc.)
   over http(s)/file/jar/... — only inline data: URIs are permitted — and
   disables scripts. This prevents SSRF and local-file disclosure from a
   user-influenced SVG (the same class of issue as a string :image source; see
   clj-pdf.utils/validate-image-url-string). Set to true to restore Batik's
   default resource loading for trusted SVG content."
  false)


(defn- ^UserAgentAdapter make-user-agent []
  (if *allow-svg-external-resources*
    (UserAgentAdapter.)
    (proxy [UserAgentAdapter] []
      ;; Batik's checkLoadExternalResource / checkLoadScript dispatch through
      ;; these getters, so overriding them enforces the policy at every load site.
      (getExternalResourceSecurity [resource-url _doc-url]
        (EmbededExternalResourceSecurity. resource-url))
      (getScriptSecurity [script-type _script-url _doc-url]
        (NoLoadScriptSecurity. script-type)))))


(defn- ^BridgeContext make-ctx []
  (let [user-agent (make-user-agent)
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
