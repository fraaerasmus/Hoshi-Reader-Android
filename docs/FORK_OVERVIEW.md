# Hoshi Custom — Fork Overview

Date: 2026-06-16

This document explains what this fork is, why it exists, and the decisions
behind how it tracks upstream. It also keeps a dated log of significant
fork-maintenance work (upstream merges and the reasoning behind reconciliation
choices). For the architecture of the app itself, see
[ARCHITECTURE.md](ARCHITECTURE.md); for user-visible change notes, see
[CHANGELOG.md](CHANGELOG.md).

## What this fork is

**Hoshi Custom** is a personal fork of the Hoshi Reader Android app
(`fraaerasmus/Hoshi-Reader-Android`, branch `hoshi-custom`). It tracks the
upstream project and layers a small set of additional features on top, then
ships them as a separately-installable, debug-signed app called **"Hoshi
Custom"** for personal sideloading.

The guiding principle is: **take all of upstream, keep only the thin slice of
genuinely-unique value this fork adds, and minimize permanent divergence.**
Every diverged line is a future merge conflict, so the fork deliberately stays
as close to upstream as it can while preserving its features.

## Relationship to upstream

- **Upstream**: `HuangAntimony/Hoshi-Reader-Android`. In this clone it is the
  git remote named **`origin`** (so `git fetch origin` pulls upstream).
- **This fork**: `fraaerasmus/Hoshi-Reader-Android`. It is the remote named
  **`fork`**, and the fork branch is **`hoshi-custom`**.
- The upstream app installs as "Hoshi Reader"; this fork's debug build installs
  alongside it as "Hoshi Custom" (`applicationId` suffix `.debug`), so both can
  coexist on one device.

## Fork features (the unique value)

These are the things this fork adds that upstream does not provide. Everything
else comes from upstream and is taken as-is.

- **18-language dictionary lookup.** Upstream's bundled dictionary engine covers
  Japanese (and, as of v1.2.0, English). This fork instead uses the
  **kaihouguide** hoshidicts engine/bridge, which de-inflects and looks up **18
  languages** (Arabic, German, Modern Greek, English, Esperanto, Spanish,
  Basque, French, Irish, Ancient Greek, Japanese, Georgian, Korean, Latin, Old
  Irish, Albanian, Tagalog, Yiddish). As of the v1.2.0 merge this is driven by
  the active dictionary **profile's** language.
- **JSON settings backup & restore.** From *Advanced > Backup*, export/import all
  app settings — and credentials (Google Drive tokens, AnkiConnect URL) — as a
  single JSON file so a fresh install can fully take over. The file holds
  credentials in plain text, so it is meant to be kept private and deleted after
  importing. Upstream has no settings/credentials backup.
- **Reader UX additions:**
  - Sasayaki playback controls on their own footer row, with configurable
    position/size and hardware-keyboard shortcuts.
  - Yomitan-style **Shift-hover** dictionary lookup (scan the word under the
    pointer without tapping), and **Esc** to dismiss the lookup popup.
  - **Multi-word phrase** scanning for space-delimited languages.

## Build & distribution

- **CI is the compiler.** There is no working local Android toolchain in this
  environment, so the fork is built on GitHub Actions
  (`.github/workflows/build-debug-apk.yml`). The release-blocking step is
  `assembleDebug`; a separate unit-test step is informational and non-blocking.
- **Debug-signed, stable keystore.** The build is signed with a committed debug
  keystore (`app/debug-signing/hoshi-debug.p12`) so in-app updates install over
  each other.
- **Commit-count versioning.** The version code/name is derived from the git
  commit count.
- **In-app update channel.** The debug build's `UPDATE_RELEASE_URL` points at
  *this fork's* releases (`fraaerasmus/...`), not upstream's, so the app updates
  itself from the fork.
- These items (`signingConfig`, "Hoshi Custom" label, debug `UPDATE_RELEASE_URL`,
  commit-count versioning) are **always kept on the fork side** during any merge.

## Merge / reconciliation philosophy

When merging upstream, every conflict or overlap is classified and handled with
a strong bias toward adopting upstream:

| Situation | Action |
|---|---|
| Upstream now ships a feature we built | Prefer dropping ours and adopting upstream's, to kill divergence. |
| Upstream added something adjacent | Union — keep both, thread ours onto upstream's new types. |
| Upstream refactored a host file our feature hooks into | Adopt upstream's architecture; re-thread our feature onto it. |
| Pure upstream addition | Take as-is. |
| Pure fork addition | Keep ours. |

---

## History log

### 2026-06-16 — Merge upstream v1.2.0

Merged 38 upstream commits (release `v1.2.0`) into `hoshi-custom`. The headline
upstream change was a new **multilingual dictionary-profiles** system, which
overlapped two fork features and forced the central decision below.

**What upstream v1.2.0 added (and how we reconciled it):**

- **Multilingual profiles.** A `profiles/` system where each profile owns a
  dictionary language plus per-profile dictionary/Anki/reader settings, with
  global, per-book, and automatic-by-book-language activation. This *supersedes*
  the fork's older "Learning profiles" feature, which we **dropped**.
- **A redesigned dictionary engine/bridge** (`HuangAntimony/hoshidicts`,
  English+Japanese only) with a new JNI API (`createLookupObject(languageId)`,
  `LookupResult.traceCandidates`, `PitchEntry.transcriptions`).
- Sasayaki color controls, reader progress-display refactor, English reader
  counts, orientation lock, recommended English dictionaries, and a
  selection-layer rewrite (language policies + per-language JS) — all taken from
  upstream and re-threaded as needed.

**Decisions and why:**

1. **Keep the fork's 18-language lookup instead of adopting upstream's
   Japanese/English-only engine.** Upstream built its own multilingual bridge
   wrapping a JA+EN-only engine (`codex/english-language-pipeline`), with a JNI
   API incompatible with the kaihouguide 18-language engine the fork uses.
   kaihouguide has not adopted upstream's new API, so the two cannot coexist —
   it is one engine or the other. We chose to **keep 18 languages**.
   - *Cost accepted:* the dictionary lookup subsystem stays permanently diverged
     from upstream (the highest-churn area), and we forgo upstream's English IPA
     transcriptions and multi-source de-inflection traces.
   - *Why it's worth it:* multilingual lookup is the fork's primary reason to
     exist; dropping 16 languages would defeat its purpose.

2. **Confine that divergence to a thin "native seam."** Upstream's app code reads
   the native dictionary types directly (`TraceCandidate`, `TraceSource`,
   `traceCandidates`, `pitch.transcriptions`). Rather than fork all of that app
   code, we kept the kaihouguide JNI constructors in `HoshiDicts.kt` and added:
   - standalone `TraceSource`/`TraceCandidate` types, and
   - **computed adapter properties** — `LookupResult.traceCandidates` synthesizes
     a single algorithm trace candidate from the engine's existing
     `deinflected`/`process`/`preprocessorSteps`, and `PitchEntry.transcriptions`
     returns empty (kaihouguide has no IPA).
   This lets *all* of upstream's dictionary UI compile unchanged against our
   engine. Trace display degrades gracefully to one candidate; IPA is absent.

3. **Adopt upstream's profile architecture for language selection, and extend it
   to 18 languages.** The fork's old `DictionaryLanguage` enum +
   `DictionarySettings.lookupLanguage` mechanism was removed. Lookup language now
   flows from the active profile's `dictionaryLanguageId` →
   `DictionaryRepository` → `DictionaryLookupQueryService.rebuild(...)` →
   `createLookupObject()` + `setLookupLanguage(session, languageId)`. We extended
   `ContentLanguageProfile.Supported` from upstream's JA+EN to all 18 kaihouguide
   languages (reusing the existing `dictionary_language_*` strings). Non-Japanese
   languages use the space-aware (English-style) selection policy and a generic
   font stack.
   - *Why:* this re-threads the fork's multilingual value onto upstream's code
     instead of maintaining a parallel mechanism, reducing divergence to just the
     `Supported` list and the native seam.

4. **Drop the fork's "Learning profiles" feature.** Upstream's profiles system
   fully supersedes it (and does more). Removed the feature, its DI/navigation
   wiring, strings, tests, and its entry in the JSON settings backup.

5. **Keep the JSON settings backup**, re-threaded onto upstream's
   profile-aware settings repositories. Upstream still has no settings/credentials
   backup, so this remains uniquely valuable. (Its now-removed `profiles` store
   was dropped since upstream's backup handles profile data.)

6. **Keep the reader UX additions** (Sasayaki footer/shortcuts, Shift-hover,
   Esc-dismiss, multi-word scan) by unioning them onto upstream's reader and
   selection-layer rewrites.

7. **Drop two upstream tests** that exercise engine features the kaihouguide
   build does not provide (multi-source trace-candidate sorting/filtering and IPA
   pitch transcriptions). They cannot be expressed against our single-candidate /
   no-transcription adapter, so keeping them would assert behavior we
   deliberately don't implement.

**Verification performed (no local compile available):** no leftover conflict
markers; no dangling references to removed symbols (`DictionaryLanguage`,
`lookupLanguage`, `LearningProfiles`); the lookup-language chain traced
end-to-end; both string locales valid with no duplicate keys; `Supported` holds
exactly 18 languages; all "always ours" CI/signing/update items intact. Final
compile and runtime smoke-testing happen on CI and device.
