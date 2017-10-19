(defproject clj-pdf "2.2.30"
  :description "PDF generation library"
  :url "https://github.com/yogthos/clj-pdf"

  :license {:name         "GNU Lesser General Public License - v 3"
            :url          "http://www.gnu.org/licenses/lgpl.html"
            :distribution :repo}

  :dependencies [[commons-codec "1.10"]
                 [org.clojure/clojure "1.6.0"]
                 [org.jfree/jfreechart "1.0.19"]
                 [org.apache.xmlgraphics/batik-bridge "1.8"]
                 [org.apache.xmlgraphics/batik-anim "1.8"]
                 [org.apache.xmlgraphics/xmlgraphics-commons "2.1"]]
  :resource-paths ["src/java"]
  :source-paths ["src/clj"]
  :java-source-paths ["src/java"]
  :javac-options ["-target" "1.6" "-source" "1.6"]

  :profiles {:dev     {:global-vars  {*warn-on-reflection* true}
                       :dependencies [[environ "1.0.1"]]
                       :plugins      [[lein-marginalia "0.7.1"]
                                      ; causing some issues on travis builds ... ? disable for now
                                      #_[lein-cloverage "1.0.6"]]}})
