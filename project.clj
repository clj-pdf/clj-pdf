(defproject clj-pdf "2.1.2"

  :description "PDF generation library"
  :url "https://github.com/yogthos/clj-pdf"

  :license {:name         "GNU Lesser General Public License - v 3"
            :url          "http://www.gnu.org/licenses/lgpl.html"
            :distribution :repo}

  :dependencies [[dom4j "1.6.1"]
                 [org.clojure/clojure "1.6.0"]
                 [org.jfree/jfreechart "1.0.15"]
                 [org.apache.xmlgraphics/batik-gvt "1.7"]
                 [org.swinglabs/pdf-renderer "1.0.5"]]

  :source-paths ["src/clj"]
  :java-source-paths ["src/java"]
  :javac-options ["-target" "1.6" "-source" "1.6"]

  :profiles {:dev     {:global-vars  {*warn-on-reflection* true}
                       :dependencies [[midje "1.7.0"]
                                      [environ "0.5.0"]
                                      [midje-readme "1.0.8"]]
                       :plugins      [[lein-marginalia "0.7.1"]
                                      [lein-midje "3.0.0"]
                                      [midje-readme "1.0.2"]]}})
