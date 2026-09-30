* 2.8.2 
- [Stop font subset/CID set flags leaking between documents](https://github.com/clj-pdf/clj-pdf/pull/250)
- [Add new include-cid-set? parameter to allow smaller PDF/A-3a compliant PDFs](https://github.com/clj-pdf/clj-pdf/pull/249)

* 2.8.1 - [fix `:align` on `:table`](https://github.com/clj-pdf/clj-pdf/pull/248), it was setting the header row count instead of the alignment. Headerless tables no longer repeat their first row on every page. Tables without an explicit `:align` stay centered as before. Also fixes a crash on an unrecognized `:valign` in `:cell`.
* 2.8.0 - security: block SSRF and local-file disclosure via image/SVG sources. **Breaking:** string `:image` sources that parse as a URL are now limited to `http`/`https` (`file:` and other schemes rejected); SVG documents no longer load external resources (only inline `data:` URIs) or run scripts. Plain file paths and non-string image sources are unaffected. Opt back in via `clj-pdf.utils/*allowed-image-url-protocols*` / `*allowed-image-url-host?*` and `clj-pdf.section.svg/*allow-svg-external-resources*`.
* 2.7.5 - fix unresolved type hint `ZapfDingbatsNumberList` in list rendering
* 2.6.9 - [Fixed colspan not affecting column counting for widths](https://github.com/clj-pdf/clj-pdf/pull/243)
* 2.6.4 - [suppor for PDF keyword](https://github.com/clj-pdf/clj-pdf/pull/233)
* 2.6.3 - fix typos, breaking `label-percision` in clj-pdf.charting renamed to `label-precision`
* 2.6.2 - update batik to 1.16
* 2.5.7 - [extract test utils into src to make them user accessible](https://github.com/clj-pdf/clj-pdf/pull/211)
* 2.5.6 - [fix for text-chunk backgrounds](https://github.com/clj-pdf/clj-pdf/commit/8c42aaf958ca3d640365ff1baa4f2ab297f46a88)
* 2.5.5 - [allow passing flag under in watermark meta](https://github.com/clj-pdf/clj-pdf/pull/207)
* 2.5.4 - updated to openpdf 1.3.20 to fix [split table truncation issue](https://github.com/clj-pdf/clj-pdf/issues/203), batik 1.13
* 2.5.3 - [further header fixes](https://github.com/clj-pdf/clj-pdf/pull/202)
* 2.5.2 - [fix for non-header text ignoring top-margin](https://github.com/clj-pdf/clj-pdf/pull/201)
* 2.5.1 - [fix for table header spacing](https://github.com/clj-pdf/clj-pdf/pull/198)
* 2.5.0 - [fix for vertical alignment in pdf-cell](https://github.com/clj-pdf/clj-pdf/pull/197)
* 2.3.1 - [only search for classes if a stylesheet is provided](https://github.com/clj-pdf/clj-pdf/pull/163)
* 2.3.0 - switch to use [OpenPDF](https://librepdf.github.io/OpenPDF/)
* 2.2.34 - expose event hooks
