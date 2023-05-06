(defproject clj-pdf "2.6.3"
  :description "PDF generation library"
  :url "https://github.com/yogthos/clj-pdf"

  :license {:name         "GNU Lesser General Public License - v 3"
            :url          "http://www.gnu.org/licenses/lgpl.html"
            :distribution :repo}

  :dependencies [[org.clojure/clojure "1.10.1" :scope "provided"]
                 [com.github.librepdf/openpdf "1.3.27"]
                 [commons-codec "1.15"]
                 [org.jfree/jfreechart "1.5.0"]
                 [org.apache.xmlgraphics/batik-bridge "1.16"]
                 [org.apache.xmlgraphics/batik-anim "1.16"]
                 [org.apache.xmlgraphics/batik-codec "1.16"]
                 [org.apache.xmlgraphics/xmlgraphics-commons "2.4"]]
  :source-paths ["src/clj"]
  :profiles {:dev {:source-paths ["src/clj" "dev"]
                   :global-vars  {*warn-on-reflection* true}
                   :dependencies [[environ "1.2.0"]
                                  [org.clojure/tools.namespace "1.0.0"]]
                   :plugins      [[lein-marginalia "0.9.1"]]}})
