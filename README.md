# PdfLabelPrinting

Android application for PDF-based label printing.  
The goal is to quickly print cut-out labels from existing PDF documents (e.g. consignment note, delivery note)
on a touch screen.

We publish the source code so that others can use it,
modify it and learn from it.

## Main features (short)

- Opening PDF files (from sharing or file picker)
- Visual cropping / preview of pages and labels
- Preparing labels for printing
- Generating a modified PDF using iText Core for Android

(The detailed functionality is documented in the code itself; this README is intentionally short.)

## Note on using signatures

The signatures created with the application **do not qualify as qualified electronic signatures,
nor as any kind of official signature regulated by law**. The app is primarily
intended for personal use / demonstration.

The developer of the application **accepts no liability** for any legal consequences
or damages arising from the use of documents created in this way; the app is used
entirely at everyone’s own risk.


## Build / development

- Minimum SDK: 24
- Target / Compile SDK: 36
- Kotlin: 2.2.x
- Android Gradle Plugin: 8.13.x

Typical build commands:

```bash
./gradlew assembleDebug
./gradlew assembleRelease
```

In Android Studio, simply import the project from the `PdfLabelPrinting` directory.


## License

The entire project (unless otherwise noted) is available under the terms of the

> **GNU Affero General Public License 3.0 or later**  
> (`AGPL-3.0-or-later`)

- A summary of the license is contained in the `LICENSE` and `NOTICE.md` files.
- The most important external dependencies and their licenses: `THIRD_PARTY_LICENSES.md`.

**Important:** the application uses the iText Core for Android (9.x) library,
which itself is available under AGPL-3.0 or a commercial license.  
If the AGPL terms are not suitable for the given project,
then you will need your own commercial iText license.


## Open source and publishing the source code

If you distribute the application (for example in the Google Play Store),
the AGPL license requires you to make the complete, modified source code
publicly available as well (under an AGPL-compatible license).

Suggested steps:

1. Create a public GitHub repository (for example `reelee81/PdfLabelPrinting`).
2. Add a link to the GitHub repository in the Play Store description.
3. Add a “License” / “About” menu item in the app that points to this information.


## Acknowledgements / Inspiration

The development of the application was inspired and bootstrapped by:

- [PDF N-Up Tool](https://github.com/Mollayo/PDF_N-Up_Tool) – An Android app to print multiple PDF pages per sheet.


## Contact

If you find a bug or would like to suggest a change, it is recommended to open a GitHub issue,
or fork the repository and send a Pull Request.