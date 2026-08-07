(ns clj-pdf.test.security
  (:require [clojure.test :refer [deftest is testing]]
            [clj-pdf.core :refer [pdf]]
            [clj-pdf.utils :as utils]
            [clj-pdf.section.svg :as svg])
  (:import [java.io ByteArrayOutputStream]
           [org.apache.batik.util ParsedURL]
           [org.apache.batik.bridge EmbededExternalResourceSecurity
            DefaultExternalResourceSecurity]))

(defn- disallowed? [s]
  (try
    (utils/validate-image-url-string s)
    false
    (catch clojure.lang.ExceptionInfo e
      (= :clj-pdf.security/disallowed-image-url
         (:type (ex-data e))))))

(deftest validate-image-url-string-defaults
  (testing "http(s) URLs are allowed and returned unchanged"
    (is (= "https://clojure.org/images/clojure-logo-120b.png"
           (utils/validate-image-url-string
             "https://clojure.org/images/clojure-logo-120b.png")))
    (is (= "http://example.com/logo.png"
           (utils/validate-image-url-string "http://example.com/logo.png"))))

  (testing "plain file paths are not URLs and pass through unchanged"
    (is (= "test/mandelbrot.jpg"
           (utils/validate-image-url-string "test/mandelbrot.jpg")))
    (is (= "/var/app/logo.png"
           (utils/validate-image-url-string "/var/app/logo.png")))
    (is (= "smiley.png"
           (utils/validate-image-url-string "smiley.png"))))

  (testing "file:// URLs are rejected (local file disclosure)"
    (is (disallowed? "file:///etc/passwd"))
    (is (disallowed? "file:///var/app/uploads/other-users-scan.png")))

  (testing "non-http(s) schemes are rejected"
    (is (disallowed? "jar:file:///tmp/x.jar!/a.png"))
    (is (disallowed? "ftp://internal/host.png")))

  (testing "SSRF to link-local / metadata host still parses as http and is allowed by protocol policy, but host allowlist can block it"
    ;; protocol policy alone allows http to any host
    (is (= "http://169.254.169.254/latest/meta-data/iam/"
           (utils/validate-image-url-string
             "http://169.254.169.254/latest/meta-data/iam/")))))

(deftest validate-image-url-string-host-allowlist
  (testing "host allowlist predicate restricts remote fetch targets"
    (binding [utils/*allowed-image-url-host?*
              (fn [host] (= host "images.example.com"))]
      (is (= "https://images.example.com/a.png"
             (utils/validate-image-url-string "https://images.example.com/a.png")))
      (is (thrown-with-msg?
            clojure.lang.ExceptionInfo #"host"
            (utils/validate-image-url-string
              "http://169.254.169.254/latest/meta-data/iam/"))))))

(deftest validate-image-url-string-protocol-override
  (testing "rebinding allowed protocols re-enables file:"
    (binding [utils/*allowed-image-url-protocols* #{"http" "https" "file"}]
      (is (= "file:///etc/hosts"
             (utils/validate-image-url-string "file:///etc/hosts"))))))

(defn- root-cause [^Throwable t]
  (if-let [c (.getCause t)] (recur c) t))

(deftest image-element-rejects-file-url
  (testing ":image with a file:// source throws when rendering"
    ;; make-section wraps the guard error in a 'failed to parse element' ex-info,
    ;; so assert on the root cause.
    (let [e (try (pdf [{} [:image "file:///etc/passwd"]] (ByteArrayOutputStream.))
                 nil
                 (catch Exception e e))]
      (is (some? e))
      (is (= :clj-pdf.security/disallowed-image-url
             (:type (ex-data (root-cause e))))))))

(deftest header-image-rejects-file-url
  (testing "header :image with a file:// source throws when rendering"
    (let [e (try (pdf [{:header [:image "file:///etc/passwd"]}
                       [:paragraph "hi"]]
                      (ByteArrayOutputStream.))
                 nil
                 (catch Exception e e))]
      (is (some? e))
      (is (= :clj-pdf.security/disallowed-image-url
             (:type (ex-data (root-cause e))))))))

;; --- SVG (Batik) external resource policy ---
;; Batik resolves resources referenced *inside* an SVG (xlink:href on <image>,
;; <use>, etc.) at build time. clj-pdf hardens the user agent so these are not
;; fetched/read by default; the enforcement point is the ExternalResourceSecurity
;; / ScriptSecurity returned by the user agent, which Batik consults before load.

(defn- ext-blocked? [ua resource-str]
  (let [sec (.getExternalResourceSecurity ua (ParsedURL. resource-str) nil)]
    (try (.checkLoadExternalResource sec) false
         (catch SecurityException _ true))))

(defn- script-blocked? [ua script-type script-str]
  (let [sec (.getScriptSecurity ua script-type (ParsedURL. script-str) nil)]
    (try (.checkLoadScript sec) false
         (catch SecurityException _ true))))

(deftest svg-blocks-external-resources-by-default
  (let [ua (#'svg/make-user-agent)]
    (testing "http external resource (SSRF) is blocked"
      (is (ext-blocked? ua "http://169.254.169.254/latest/meta-data/iam/")))
    (testing "file external resource (local read) is blocked"
      (is (ext-blocked? ua "file:///etc/passwd")))
    (testing "jar/ftp external resources are blocked"
      (is (ext-blocked? ua "jar:file:///tmp/x.jar!/a.png"))
      (is (ext-blocked? ua "ftp://internal/host.png")))
    (testing "inline data: URIs are still permitted (no fetch)"
      (is (not (ext-blocked? ua "data:image/png;base64,iVBORw0KGgo="))))
    (testing "scripts are blocked"
      (is (script-blocked? ua "text/ecmascript" "http://evil.example/x.js")))))

(deftest svg-policy-security-impls
  (testing "default policy installs data-only (Embeded) external resource security"
    (let [ua  (#'svg/make-user-agent)
          sec (.getExternalResourceSecurity ua (ParsedURL. "http://x/y") nil)]
      (is (instance? EmbededExternalResourceSecurity sec))))
  (testing "opt-in restores Batik's default security impl"
    (binding [svg/*allow-svg-external-resources* true]
      (let [ua  (#'svg/make-user-agent)
            sec (.getExternalResourceSecurity ua (ParsedURL. "http://x/y") nil)]
        (is (instance? DefaultExternalResourceSecurity sec))))))

(deftest svg-benign-inline-still-renders
  (testing "an SVG with no external references renders without error"
    (let [out (ByteArrayOutputStream.)]
      (pdf [{} [:svg {} (str "<?xml version=\"1.0\"?>"
                             "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"10\" height=\"10\">"
                             "<rect x=\"1\" y=\"1\" width=\"5\" height=\"5\"/></svg>")]]
           out)
      (is (pos? (alength (.toByteArray out)))))))
