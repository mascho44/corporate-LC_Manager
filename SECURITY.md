# Sicherheitsmeldungen

Sicherheitslücken bitte nicht mit Zugangsdaten, Originaldokumenten oder
Exploit-Details in öffentlichen Issues melden. Verwenden Sie die vertrauliche
[GitHub-Sicherheitsmeldung](https://github.com/mascho44/corporate-LC_Manager/security/advisories/new).

Beschreiben Sie betroffenen Stand, Auswirkungen und reproduzierbare Schritte
mit anonymisierten Testdaten. Niemals Produktionspasswörter, API-Schlüssel
oder reale Akkreditivdokumente mitsenden.

Sicherheitskorrekturen werden zunächst für den aktuellen Stand von `main`
bereitgestellt. Das Projekt ist in aktiver Entwicklung; eine garantierte
Reaktionszeit oder langfristige Versionspflege wird nicht zugesagt.

## Prüfungen

CI prüft Java-17-Build, Java-/JavaScript-Tests und Shell-Syntax.
Dependency Review prüft neue hohe/kritische Schwachstellen in Pull Requests.
Dependabot berücksichtigt Maven, Docker und GitHub Actions.
Native GitHub-Funktionen ergänzen Secret Scanning, Push Protection und CodeQL.
Repository-Einstellungen und Branch-Schutz werden separat auf GitHub aktiviert.

Details: [Upload- und Backup-Sicherheit](docs/betriebssicherheit.md).
