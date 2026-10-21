# Mehrsprachigkeit: Leitfaden für die Entwicklung

Die Oberfläche ist zweisprachig (Deutsch und Englisch). **Jeder neue sichtbare Text bekommt beide Sprachen von Anfang an.** Ein späteres Nachziehen hat bei den bestehenden Bildschirmen viele Pull Requests gekostet und soll sich nicht wiederholen.

## Sprachpakete

- `src/main/resources/static/language-en.json` und `language-de.json`: gleiche Schlüssel, gleiche Platzhalter (`{n}`, `{name}`). Der deutsche Wert entspricht dem deutschen Rückfalltext im Code.
- Schlüssel sind kurz und nach Bereich gegliedert (`ds.title`, `inbox.confirm`, `audit.act.LOGIN`). Mehrzahl und Einzahl bekommen getrennte Schlüssel (`corp.blockingOne` und `corp.blockingMany`), weil Englisch und Deutsch sie verschieden bilden.
- Die Sprache steht in `LcI18n` (`LcI18n.language()`, `LcI18n.t(key, values)`). Fehlt ein Schlüssel, erscheint der Rückfalltext.

## Texte im Frontend

| Wo | So |
|---|---|
| `app.js` | `tx('schluessel','Deutscher Rückfalltext',{wert:...})` |
| andere Skripte | lokaler Helfer `tr(...)` (siehe `kpis.js`, `document-set.js`), gleiche Form |
| statisches HTML | `data-i18n="schluessel"` und `data-i18n-placeholder="schluessel"` |
| innerhalb eines einfach angeführten JS-Strings | `'...'+tx('k','Text')+'...'`; `${...}` wirkt dort nicht |

Weitere Regeln:

- Keine deutschen Klartext-Strings in neuem Oberflächencode, auch nicht in Fehlermeldungen, `alert`, `confirm`, `placeholder`, `title` und `aria-label`.
- Daten- und Zahlenformate folgen der Sprache (`LcI18n.locale()`, nicht fest `de-DE`).
- **Nicht übersetzte Texte nie als Schlüssel oder Vergleichswert benutzen.** Abschnitte der Akte werden deshalb über `data-sect` erkannt, nicht über ihre Überschrift.
- Dialoge und Knöpfe, die einmal gebaut werden, müssen beim Sprachwechsel neu aufgebaut werden. Das Ereignis heißt `lc-language-changed` (Beispiel in `kpis.js`).

## Texte vom Server

Der Server liefert keine fertig übersetzten Sätze, sondern eines von beidem:

1. **Ein Feld mit englischem Gegenstück**, zum Beispiel `message`/`messageEn` und `advice`/`adviceEn` am Prüfbefund (`FindingText`, `CorrectionAdvice`) oder `hintEn` am Planpunkt des Beschaffungsplans. Das Frontend wählt nach `LcI18n.language()` und fällt auf den deutschen Text zurück.
2. **Einen Code**, den das Frontend übersetzt. Beispiel: Audit-Ereignisse (`audit.act.<CODE>`, Funktion `auditLabel()`).

Jede neue Audit-Aktion (`audit.record(..., "NEUE_AKTION", ...)`) braucht Bezeichnungen `audit.act.NEUE_AKTION` in beiden Sprachpaketen. Jeder neue feste Prüfbefund-Code braucht einen englischen Text in `FindingText`; ein Test (`FindingTextTest`) prüft das für die festen Codes der Prüfung.

Dokumente, die die App erzeugt (Rechnung, Packliste, Zertifikate, Begleitschreiben, Änderungsantrag), sind englisch, weil sie an Banken und Handelspartner gehen. Sie hängen nicht von der Sprache der Oberfläche ab.

## Prüfen

- `node --test src/test/js/*.cjs`: `i18n-guard.test.cjs` prüft Schlüsselgleichheit der Sprachpakete und dass die übersetzten Skripte keinen deutschen Klartext enthalten. **Neue Skripte neuer Funktionen in die Liste `TRANSLATED` aufnehmen.** `i18n-corporate.test.cjs` prüft, dass jeder in `app.js` verwendete Schlüssel in beiden Paketen steht, und rendert wichtige Bereiche in beiden Sprachen.
- Neue Bildschirme zusätzlich in Englisch ansehen (lokaler Stack: `scripts/layout-stack.sh up`) und nach übrig gebliebenem Deutsch suchen.
- Beim Rendern in Tests auf unaufgelöste `${` und `tx(` im Ergebnis prüfen.

## Stand

Zweisprachig sind der Corporate-Arbeitsablauf, die Befunde, Verlauf und Audit, Aktenansicht und -liste, Posteingang (Kern), Beschaffungsplan, Entwürfe, Menü-Übersichten. Noch deutsch sind Spezialfunktionen (Sammel-PDF aufteilen, Layoutlernen, Training, Prüfbereiche der Bank), die Fehlermeldungen des Servers und Teile der Plattformverwaltung. Beim Anfassen eines dieser Bereiche gleich mit übersetzen.
