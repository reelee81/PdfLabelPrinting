# Third‑party libraries and licenses

This project uses several third‑party libraries.  
Below is a non‑exhaustive but important list of runtime and test dependencies,
together with the license that applies to each upstream project.

For the full license texts, please refer to the linked pages.

---

## Core runtime libraries (Apache License 2.0)

These libraries are part of AndroidX / Material and are covered by
the **Apache License 2.0**:

- `androidx.core:core-ktx:1.17.0`
- `androidx.appcompat:appcompat:1.7.1`
- `com.google.android.material:material:1.13.0`
- `androidx.activity:activity:1.11.0`
- `androidx.constraintlayout:constraintlayout:2.2.1`

Many additional transitive AndroidX modules may also be pulled in at build time.
They are usually licensed under Apache 2.0 as well.

References (examples):
- <https://developer.android.com/jetpack/androidx>
- <https://www.apache.org/licenses/LICENSE-2.0>


## PDF viewer (Apache License 2.0)

- `androidx.pdf:pdf-viewer-fragment:1.0.0-alpha11`

This PDF viewer fragment is part of AndroidX and is also licensed under
the Apache License 2.0.

Reference: <https://developer.android.com/media/grow/implement-pdf-viewer>


## iText Core for Android (AGPL-3.0)

The app uses **iText Core for Android 9.3.0** via the following modules:

- `com.itextpdf.android:kernel-android:9.3.0`
- `com.itextpdf.android:layout-android:9.3.0`
- `com.itextpdf.android:bouncy-castle-adapter-android:9.3.0`
- `com.itextpdf.android:bouncy-castle-connector-android:9.3.0`

These libraries are licensed under the **GNU Affero General Public License v3**,
or can be used under a separate commercial license purchased from iText.

References:
- <https://itextpdf.com/>
- <https://github.com/itext/itext-java>
- <https://www.gnu.org/licenses/agpl-3.0.html>


## Core library desugaring (OpenJDK / GPLv2 with Classpath Exception)

- `com.android.tools:desugar_jdk_libs_nio:2.1.5`

This is a Google‑provided subset of OpenJDK libraries that enables
`java.nio` and other newer JDK APIs on older Android API levels.

The project itself is hosted by Google and combines code under
**GPLv2 with the Classpath Exception** from OpenJDK with Google’s
build tooling.

References:
- <https://github.com/google/desugar_jdk_libs>


## Test libraries

These are used only in unit or instrumented tests:

- `junit:junit:4.13.2` – Eclipse Public License 1.0  
  <https://junit.org/junit4/>

- `androidx.test.ext:junit:1.3.0` – Apache License 2.0
- `androidx.test.espresso:espresso-core:3.7.0` – Apache License 2.0

AndroidX test components are generally licensed under Apache 2.0:
<https://developer.android.com/testing>


---

This file is provided for convenience only and is not a legal document.  
If you use or redistribute this project, you are responsible for verifying
the current licenses of all dependencies involved.
