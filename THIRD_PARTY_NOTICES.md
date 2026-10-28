# Third-party notices

Simple LED Clock original code is licensed under **GPL-3.0-only**.
Third-party components retain their own copyright notices and licenses.

## Application dependencies

The resolved `modernReleaseRuntimeClasspath` contains:

| Component | Version | License |
| --- | --- | --- |
| Kotlin standard library | 2.1.20 | Apache-2.0, with upstream third-party notices below |
| JetBrains annotations | 13.0 | Apache-2.0 |

Kotlin: Copyright JetBrains s.r.o. and Kotlin Programming Language contributors.
JetBrains annotations: Copyright JetBrains s.r.o.
Apache-2.0 is compatible with GPLv3; it does not become GPL-3.0-only.
The Kotlin JVM standard library also documents Apache-2.0 code derived from
GWT (Google Inc.) and Guava (The Guava Authors), BSD-3-Clause code from ThreeTenBP
(Stephen Colebourne and Michael Nascimento Santos), and Boost-1.0 math code
(Eric Ford and Hubert Holin). These licenses are compatible with GPLv3.
The corresponding original license texts and attributions are in `LICENSES/`.

Sources:
- https://github.com/JetBrains/kotlin/blob/v2.1.20/license/README.md
- https://github.com/JetBrains/kotlin/blob/v2.1.20/license/LICENSE.txt
- https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/2.1.20/kotlin-stdlib-2.1.20.pom
- https://repo.maven.apache.org/maven2/org/jetbrains/annotations/13.0/annotations-13.0.pom

Android framework APIs are supplied by the operating system; the Android SDK
is a build prerequisite and is not redistributed by this repository.

## Build and test tools

Gradle Wrapper retains its Apache-2.0 headers; the license and Gradle notice
are preserved in `LICENSES/Apache-2.0.txt` and `LICENSES/Gradle-NOTICE.txt`.
AGP and the Kotlin compiler are build tools and are not packaged in the APK.

JUnit 4.13.2 uses EPL-1.0. **EPL-1.0 is not GPLv3-compatible.** JUnit is used only
for running local JVM tests, and neither JUnit binaries nor compiled combined
test artifacts are distributed in this repository or the application APK.
Do not redistribute a combined GPL/EPL-1.0 test binary without a separate review.
Robolectric 4.14.1 / 4.11.1 uses MIT and is also test-only.
Test framework transitive dependencies are not application runtime dependencies.

- https://junit.org/junit4/license.html
- https://github.com/robolectric/robolectric/blob/robolectric-4.14.1/LICENSE
- https://www.gnu.org/licenses/license-list.html

The APK signing certificate identifies the signer; no private signing material
is included. License notices do not change the existing signing configuration.
