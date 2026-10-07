# Fit baseline

Saved on 2026-10-07 at 10:18:02 (Europe/Budapest), before the clipping and
polyline stroke measurement changes.

`project-source.tar.gz` contains tracked files and non-ignored untracked source
files, including the existing local edits. Test PDF collections, build outputs,
IDE-local settings, signing keys, checkpoints and Git history are not included.
The archive was compared with the workspace before making changes.

SHA-256:

```text
project-source.tar.gz
d4075d84c6f2d8e09ceaf3cc8942aeb6c84c08fd6619dc48e33d1bf843ac55e6

MainActivity.kt
6353f99b03ff734ce9eb9eb2ae301548424e0a8f4dddb7fbadc7a1f7744f5893

PdfFitGeometry.kt
66ab217c3b2a681dfdfdd5c9b5ed3e1b3a0925c2671ee60081486b3cb83ca882

PdfFitGeometryTest.kt
c2e53281cbd5a29d108485730cff6816434ff9a16a9175f5041725a6aa513d12
```

For restoration, extract into a separate directory and compare the affected
files first. Restore only the requested files, preserving unrelated later edits.
`PdfStrokeBounds.kt` was added after this checkpoint and is not in the archive.
