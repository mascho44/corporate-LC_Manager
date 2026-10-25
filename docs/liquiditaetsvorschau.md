# Liquiditätsvorschau (Corporate-Profil)

Die Seite **Liquiditätsvorschau** zeigt, wann aus den offenen Akkreditiven Geld erwartet oder fällig wird. Sie ist nur für Mandanten mit **Corporate-Profil** vorhanden (auch im kombinierten Profil); für reine Bank-Mandanten fehlt der Menüpunkt, die Schnittstelle `GET /api/liquidity` antwortet mit „nicht verfügbar“.

## Was gezählt wird
- **Eingänge:** Akten, in denen das Unternehmen **Begünstigter** ist (Export-Akkreditive).
- **Ausgänge:** Akten, in denen das Unternehmen **Auftraggeber** ist (Import-Akkreditive): bei Fälligkeit muss gezahlt werden.
- **Termin:** der früheste „Voraussichtliche Fälligkeit“ aus den [Fristen](erwarteter-geldeingang.md). Akten ohne erkennbaren Termin stehen in der Zeile **Ohne Termin**; liegt der Termin in der Vergangenheit und ist noch nicht alles bezahlt, in der Zeile **Überfällig**.
- **Betrag:** die Summe der Handelsrechnungen der Akte in der Akkreditivwährung (höchstens der Akkreditivbetrag), sonst der Akkreditivbetrag, **abzüglich** der erfassten [Zahlungen](zahlungsstatus.md). Vollständig bezahlte Akten fehlen.
- Nicht gezählt: geschlossene oder abgelaufene Akten, Garantien und Zahlungen in anderer Währung als der des Akkreditivs.

## Darstellung
Je Währung eine Tabelle mit Eingängen, Ausgängen und Saldo, nach **Kalenderwochen** (13 Wochen) oder **Monaten** (6 Monate). Darunter die einzelnen Posten mit Termin, Akte, Gegenpartei, Richtung, Betrag und Grundlage (Rechnung oder Akkreditivbetrag). „Als CSV speichern“ exportiert die Tabelle (Semikolon, UTF-8).

## Grenzen
- Alles ist eine **Schätzung** aus der Zahlungsklausel; erkannt werden nur die Fälle aus [Erwarteter Geldeingang](erwarteter-geldeingang.md).
- Berücksichtigt werden höchstens 500 Akten (ein Hinweis erscheint, wenn es mehr gibt).
- Keine Umrechnung zwischen Währungen und kein Abgleich mit dem Bankkonto.
