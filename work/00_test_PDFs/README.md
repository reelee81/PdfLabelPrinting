# 00_test_PDFs

Put the PDF files used by the tests here. Any folder structure can be used: the tests search every subfolder.

Regression tests for earlier fixes look up these files by their exact name:

| File | Source | Test |
|---|---|---|
| `bug1737260.pdf` | [pdf.js test files](https://github.com/mozilla/pdf.js/tree/master/test/pdfs) | importing a page with optional content layers |
| `bug920426.pdf` | [pdf.js test files](https://github.com/mozilla/pdf.js/tree/master/test/pdfs) | repairing a malformed Identity CMap |
| `issue9418.pdf` | [pdf.js test files](https://github.com/mozilla/pdf.js/tree/master/test/pdfs) | opening an overwritten file from its earlier revision |
| `160F-2019.pdf` | [pdf.js test files](https://github.com/mozilla/pdf.js/tree/master/test/pdfs) | importing a form with merged push button fields |

The form and annotation import test uses every PDF in a subfolder named `Forms_and_annotations`
(under any file and subfolder names).

If a file or folder is missing, the test that needs it is skipped, not failed.

The tests only read this folder. Their output goes to `work/00_test_PDFs_work`.

Only this README is tracked by git; the PDF files placed here are never committed.
