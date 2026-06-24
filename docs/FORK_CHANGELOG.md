# Fork Changelog

User-visible changes that **Hoshi Custom** adds on top of upstream Hoshi Reader.
Upstream's own release notes are mirrored in [CHANGELOG.md](CHANGELOG.md); this file
covers only the fork's unique features.

Hoshi Custom ships continuously with commit-count versions (e.g. `v1.2.412`) rather than
cutting semantic releases, so per-build notes live on
[GitHub Releases](https://github.com/fraaerasmus/Hoshi-Reader-Android/releases). The list
below is a single living summary of everything the fork adds, grouped Added / Fixed.

## Added

- Extend dictionary lookup to all 18 languages supported by the kaihouguide hoshidicts engine. Set a profile's language (e.g. German, Korean, Spanish, Latin) and reader popups, recursive lookups, Dictionary search, and Android Process Text all look up in that language. Built on the upstream dictionary profiles and the kaihouguide multilingual bridge rather than upstream's Japanese/English-only engine; in exchange, English IPA transcriptions and multi-source deinflection traces from the upstream engine are unavailable in this fork.
- Add settings backup and restore as a JSON file from Advanced > Backup, including Google Drive and Anki credentials so a new install can take over; the file holds those credentials in plain text, so keep it private and delete it after importing.
- Add multi-word phrase lookup so tapping a word in space-delimited languages (e.g. French "coup de main") matches dictionary entries that span spaces, with a "Scan Multi-Word Phrases" toggle in Dictionary settings; the reader tap scan now follows the configured scan length.
- Add reader Appearance controls to position the Sasayaki playback buttons (rewind/play/forward) on their own row on the left or centered, and to scale their size from 100% to 200%.
- Add hardware keyboard shortcuts for Sasayaki playback while audio is loaded: Space or K to play/pause, Left arrow or J to rewind, and Right arrow or L to skip forward; the keys stay out of the way while you are typing in a search field.
- Add an Esc keyboard shortcut to dismiss the dictionary lookup popup.
- Add Yomitan-style Shift-hover dictionary lookup: with a mouse or trackpad, hold Shift and the word under the pointer is scanned instantly without tapping, re-scanning as you move. Toggle it with "Scan Word on Shift Hover" in Dictionary settings (on by default).
- Mine words looked up inside a popup (nested lookups) with the original book sentence and Sasayaki audio instead of the popup's own text, so drilling into a definition still produces a card with the reading context. Toggle it with "Mine nested lookups with reading context" in Dictionary settings (on by default).
- Add a "Show Chapter Information" toggle in reader Appearance > Display that shows the current chapter title with your page position within that chapter, e.g. "The Boy Who Lived (3/17)", using the EPUB's table of contents (hidden for books without one); off by default.
- Add an "Information Position" control in reader Appearance > Display to align the reader info (title, chapter, progress, percentage) to the left, center, or right.
- Hold the Sasayaki rewind or forward control to keep skipping until you let go, instead of tapping once per step. Works for the on-screen buttons and the hardware seek keys (J/L, the arrow keys, and the volume keys when set to seek); a quick tap still skips a single step.
- Support headphone and Bluetooth rewind/forward gestures for Sasayaki: the forward gesture skips forward and the back gesture skips backward, matching the Skip Action setting (sentence cue or N seconds). Previously only play/pause responded to headphone controls.
- Add edge-swipe brightness and volume controls: with "Edge Swipe Brightness & Volume" enabled in reader Behavior settings (off by default), drag vertically along the left edge to adjust screen brightness or the right edge to adjust media volume, with a brief on-screen level indicator. Brightness applies to the reader only and reverts to the system setting when you leave the reader. Available in Paginated and Visual Novel reading modes.
- Keep Sasayaki audio playing in the background: it now continues when you leave the reader, switch apps, or lock the screen — with a media notification and lock-screen transport controls — instead of stopping after about a minute. Backed by a foreground media service; returning to the reader resyncs the highlight.
- Add curated color presets (Rosé Pine, Gruvbox, Everforest) to the reader's Custom theme via a Preset dropdown in Appearance > Theme. The Interface (System / Light / Dark) control selects each preset's light or dark variant, so System follows the device — e.g. Rosé Pine shows the light "Dawn" palette in light mode and the dark palette in dark mode. Defaults to Rosé Pine; pick "Custom" in the dropdown to hand-set background, text, and info colors as before.
- Add a now-playing mini-player: once Sasayaki audio is playing, a bar appears above the tab bar on the bookshelf and other screens showing the book cover, title, rewind/play/forward, and a progress line. Tap it to jump straight back into the book; it stays put as you move around the app.
- Add a Sasayaki sleep timer in the playback sheet: stop after 15/30/45/60 minutes or at the end of the current chapter. It counts down in the background and pauses playback when it elapses.
- Add a playback-speed button to the Sasayaki media notification and lock screen that cycles through 1.0×–2.0×; the lock-screen scrubber already lets you seek within the audio.

## Fixed

- Make the Sasayaki lock-screen and notification skip buttons work: with a single audio file the player never exposed next/previous, so those buttons (especially next) did nothing. They now rewind/forward by the Skip Action setting like the on-screen and headphone controls.
- Look up the whole word when tapping or hovering the middle of a word in non-English space-delimited languages (e.g. the "i" in French "jamais" now finds "jamais", not "is"), matching the reader popup and English behavior. The reader previously scanned forward from the tapped character for those languages.
- Space out inflected-form ("form of") dictionary entries so the lemma and its grammatical description no longer run together (e.g. French "détestait" now shows "détester third-person singular imperfect indicative"), with multiple senses on separate lines.
- Look up French elided words by also searching the form without the leading article/clitic (e.g. "l'homme" now finds "homme", "d'accord" finds "accord", "qu'il" finds "il"); the content word is listed first, the original article form is still searched too, and the reader highlights the whole tapped word.
- Lead with a word's actual definition when looking up an inflected form: dictionary "form of" entries that only point to the base word (e.g. French "détestait" → "détester third-person singular imperfect indicative") now sink below the real definition ("détester" — to hate, to detest), which previously required scrolling. The inflection entry still appears below.
