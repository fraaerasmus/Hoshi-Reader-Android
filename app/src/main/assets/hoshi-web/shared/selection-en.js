//
//  selection-en.js
//  Hoshi Reader
//
//  Copyright © 2026 Antimony.
//  SPDX-License-Identifier: GPL-3.0-or-later
//

(function() {
    const EnglishScanDelimiters = '"“”„‟\'‘’‚‛«»‹›!?—–-‐‑‒/\\|@#$%^&*_+=~`<>';
    const EnglishWordInternalDelimiters = '\'’`-‐‑';

    function isEnglishWordChar(char) {
        return !!char && /[\p{L}\p{N}]/u.test(char);
    }

    function isWordInternalDelimiter(text, offset) {
        const char = window.hoshiSelection?.codePointAt(text, offset) ?? text[offset];
        return EnglishWordInternalDelimiters.includes(char) &&
            isEnglishWordChar(text[offset - 1]) &&
            isEnglishWordChar(text[offset + 1]);
    }

    function isEnglishScanBoundary(text, offset, selection) {
        const char = selection.codePointAt?.(text, offset) ?? text[offset];
        return selection.scanDelimiters.includes(char) ||
            (EnglishScanDelimiters.includes(char) && !isWordInternalDelimiter(text, offset));
    }

    const EnglishSelectionLanguage = {
        isScanBoundary(char, selection) {
            return selection.scanDelimiters.includes(char) || EnglishScanDelimiters.includes(char);
        },

        isScanBoundaryAt(text, offset, selection) {
            return isEnglishScanBoundary(text, offset, selection);
        },

        isHitBoundary(char, selection) {
            return /^[\s\u3000]$/.test(char) || this.isScanBoundary(char, selection);
        },

        isHitBoundaryAt(text, offset, selection) {
            const char = selection.codePointAt?.(text, offset) ?? text[offset];
            return /^[\s\u3000]$/.test(char) || isEnglishScanBoundary(text, offset, selection);
        },

        isWordStartBoundary(char, selection) {
            return this.isHitBoundary(char, selection);
        },

        isWordStartBoundaryAt(text, offset, selection) {
            return this.isHitBoundaryAt(text, offset, selection);
        },

        selectionStartForHit(hit, selection) {
            return selection.findWordStart(hit);
        },
    };

    const FrenchElisionClitics = ['l', 'd', 'j', 'n', 's', 't', 'c', 'm', 'qu'];

    function isFrenchElisionApostrophe(text, offset) {
        const char = window.hoshiSelection?.codePointAt(text, offset) ?? text[offset];
        if (char !== '\'' && char !== '’') return false;
        if (!isEnglishWordChar(text[offset - 1]) || !isEnglishWordChar(text[offset + 1])) return false;
        let start = offset;
        while (start > 0 && isEnglishWordChar(text[start - 1])) start--;
        return FrenchElisionClitics.includes(text.slice(start, offset).toLowerCase());
    }

    const FrenchSelectionLanguage = {
        ...EnglishSelectionLanguage,

        // Word starts break after l'/d'/qu'… so tapping "homme" in "l'homme" scans "homme";
        // forward scans keep the apostrophe word-internal (aujourd'hui, and taps on the clitic).
        isWordStartBoundaryAt(text, offset, selection) {
            return EnglishSelectionLanguage.isHitBoundaryAt.call(this, text, offset, selection) ||
                isFrenchElisionApostrophe(text, offset);
        },
    };

    // Default for any space-delimited language (this file is loaded for all non-Japanese profiles).
    window.hoshiSelectionLanguagePolicies = {
        ...window.hoshiSelectionLanguagePolicies,
        en: EnglishSelectionLanguage,
        fr: FrenchSelectionLanguage,
        default: EnglishSelectionLanguage,
    };
})();
