(defproject org.clojars.coyotespike/clj-pdf "2.0.6"
  :description "PDF generation library"
  :url "https://github.com/yogthos/clj-pdf"
  :license {:name "GNU Lesser General Public License - v 3"
            :url "http://www.gnu.org/licenses/lgpl.html"
            :distribution :repo
            :comments "same as  iText and JFreeChart"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.jfree/jfreechart "1.0.15"]
;                 [com.lowagie/itext "4.2.1"]
                 [com.itextpdf/itextpdf "5.0.6"]
                 [org.apache.xmlgraphics/batik-gvt "1.7"]]

  :plugins [[lein-marginalia "0.7.1"]
            [lein-midje "3.0.0"]
            [midje-readme "1.0.2"]]

  :signing {:gpg-key "timothy.roy@protonmail.ch"}
  :profiles {:dev {:global-vars {*warn-on-reflection* true}
                   :dependencies [[midje "1.5.0"]
                                  [environ "0.5.0"]
                                  [midje-readme "1.0.2"]]}})
