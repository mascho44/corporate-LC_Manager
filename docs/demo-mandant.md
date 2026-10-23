# Demo-Mandant befüllen

`scripts/seed-demo.cjs` legt den Mandanten `demo` (nur Corporate) an und füllt ihn mit erfundenen, realistischen Fällen: 6 Akkreditive (Export und Import, offen, knapp vor Ablauf, abgelaufen, mit Zahlungen), 6 Garantien (aktiv, Antrag gesendet, zurückgegeben), 3 Avallinien (eine nahe der Warnschwelle) und Kunden. Alle Daten sind relativ zum heutigen Tag, ein erneuter Lauf zeigt also immer frische Fristen.

```bash
LCM_URL=https://lc.ostms.de LCM_USER=<Plattform-Admin> LCM_PASSWORD=<Passwort> LCM_TOTP=<aktueller Code> node scripts/seed-demo.cjs
```

- Benötigt einen Plattform-Administrator; dieser wird erster Administrator des Mandanten.
- Idempotent: vorhandene Akten (Referenz), Garantien, Linien und Kunden werden übersprungen. Für frische Fristen den Mandanten in der Plattformverwaltung löschen und neu füllen.
- Es werden nur öffentliche API-Aufrufe genutzt, keine Direktzugriffe auf die Datenbank.
