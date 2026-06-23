# Hoshi Custom — Fork Overview

Date: 2026-06-23

This document explains what this fork is, why it exists, and the decisions
behind how it tracks upstream. It also keeps a dated log of significant
fork-maintenance work (upstream merges and the reasoning behind reconciliation
choices). For the architecture of the app itself, see
[ARCHITECTURE.md](ARCHITECTURE.md); for this fork's own change notes, see
[FORK_CHANGELOG.md](FORK_CHANGELOG.md) (upstream's release notes are mirrored in
[CHANGELOG.md](CHANGELOG.md)).

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
  the active dictionary **profile's** language. Lookup quality for these
  languages is also refined: whole-word scanning when tapping mid-word in
  space-delimited languages, French elided forms (`l'homme` → `homme`),
  spaced-out inflected-form glossary entries, and ranking a word's real
  definition above bare "form of" pointers.
- **JSON settings backup & restore.** From *Advanced > Backup*, export/import all
  app settings — and credentials (Google Drive tokens, AnkiConnect URL) — as a
  single JSON file so a fresh install can fully take over. The file holds
  credentials in plain text, so it is meant to be kept private and deleted after
  importing. Upstream has no settings/credentials backup.
- **Reader UX additions:**
  - Sasayaki playback controls on their own footer row, with configurable
    position/size and hardware-keyboard shortcuts; hold the rewind/forward
    controls — on-screen, hardware keys, or headphone/Bluetooth — to seek
    continuously.
  - Yomitan-style **Shift-hover** dictionary lookup (scan the word under the
    pointer without tapping), and **Esc** to dismiss the lookup popup.
  - **Multi-word phrase** scanning for space-delimited languages.
  - **Nested-lookup mining** that keeps the original book sentence and Sasayaki
    audio when drilling into a definition.
  - Reader footer **chapter information** (chapter title plus in-chapter page
    position) and a left/center/right **Information Position** control.

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

### 2026-06-21 — Merge upstream v1.2.2

Merged upstream through **v1.2.2** (`60a5263`) — reader in-book search, Android 8.0/8.1
support, and assorted VN/Reader fixes, all taken as-is. The fork's reader features were
re-threaded onto upstream's changes; this merge added no new permanent divergence beyond
the existing dictionary-engine seam.

Alongside the merge, a batch of fork features landed (2026-06-21 → 23): nested-lookup
mining with the original reading context; reader footer chapter info and a
left/center/right Information Position control; hold-to-seek for the Sasayaki
rewind/forward controls, including headphone and Bluetooth gestures; and
dictionary-quality refinements (whole-word scanning for space-delimited languages, French
elided forms, and better inflected-form ranking).

Fork change notes now live in [FORK_CHANGELOG.md](FORK_CHANGELOG.md); `CHANGELOG.md` is
kept as a clean mirror of upstream's release notes.

### 2026-06-16 — Merge upstream v1.2.0

Merged 38 upstream commits (`v1.2.0`). Upstream introduced a multilingual
dictionary-**profiles** system and a redesigned JA+EN-only dictionary engine
(new JNI API: `createLookupObject(languageId)`, `LookupResult.traceCandidates`,
`PitchEntry.transcriptions`), overlapping the fork's multilingual lookup and
Learning profiles.

Key decisions:

- **Kept the kaihouguide 18-language engine** over upstream's JA+EN engine (the
  two JNI APIs are incompatible; can't run both). Trade-off: the dictionary
  subsystem stays permanently diverged, and we forgo upstream's IPA
  transcriptions and multi-source traces.
- **Native seam in `HoshiDicts.kt`:** keep the kaihouguide JNI constructors; add
  `TraceSource`/`TraceCandidate` types + **computed** `LookupResult.traceCandidates`
  (one algorithm candidate from `deinflected`/`process`/`preprocessorSteps`) and
  `PitchEntry.transcriptions` (empty). Upstream's dictionary UI then compiles
  unchanged against our engine.
- **Language is now profile-driven:** removed `DictionaryLanguage` /
  `DictionarySettings.lookupLanguage`; lookup language flows from the profile's
  `dictionaryLanguageId` → `DictionaryLookupQueryService.rebuild(...)` →
  `setLookupLanguage`. Extended `ContentLanguageProfile.Supported` to all 18
  languages. Non-JA uses the English (space-aware) selection policy.
- **Dropped Learning profiles** (superseded by upstream's `profiles/`); **kept**
  JSON settings backup (minus its profiles store) and the reader UX additions,
  re-threaded onto upstream's refactors.
- **Dropped 2 upstream tests** for engine features we lack (multi-source traces,
  IPA transcriptions).

Two follow-up fixes after the first CI run: de-duplicated `Profiles` enum
entries / `when` branches / an import that the merge added on both sides with no
conflict marker (`5d93cfa`). Released as **Hoshi Custom v1.2.402**.
