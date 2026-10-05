# Corporate LC Manager – Roadmap

## Als Nächstes

- Eigenständiger Microservice `lc-rule-engine`: gemeinsame UCP-/ISBP-Prüfengine mit versionierten, dokumenttypspezifischen Rule Packs. Zielarchitektur, Phasen und Abnahmekriterien stehen in [lc-rule-engine](docs/lc-rule-engine.md). Bezug: GitHub #29 und #39. Noch nicht ausgelagert oder deployed.
- RabbitMQ-Adapter bei konkretem externem Integrationsbedarf aktivieren
- Löschkonzept erstellen und umsetzen: Datenarten und Aufbewahrungsfristen festlegen, gesetzliche und geschäftliche Löschsperren berücksichtigen, Löschrechte und Vier-Augen-Freigabe für irreversible Löschungen einführen. Originaldateien, OCR-Daten, Trainingsvorlagen, Lerninhalte und Backups ausdrücklich behandeln; Wiederherstellbarkeit, Löschberichte und Audit-Nachweise definieren.
- OFAC- und EU-Sanktionslisten anbinden: täglicher Abruf der OFAC-SDN-/Non-SDN-Listen und der konsolidierten EU-Finanzsanktionsliste, XML-/CSV-Import mit Quelle, Versionsstand, Abrufzeit und Prüfsumme. Antragsteller, Begünstigte, Banken und weitere Beteiligte einschließlich Aliasnamen prüfen; mögliche Treffer fachlich bewerten, Warn-/Sperrlogik und erneutes Screening bei Listenänderungen vorsehen. Veraltete oder nicht verfügbare Listen sichtbar machen und Prüfungen sowie Entscheidungen auditieren.
- Compliance-Zugang und Compliance-Rolle: eigener Arbeitsbereich für Screening-Treffer, blockierte Vorgänge und Eskalationen; getrennte Rechte für Sachbearbeitung, Compliance und Administration. Freigaben, Ablehnungen und Eskalationen mit Begründung protokollieren; schreibgeschützten Prüfzugang für interne Revision vorsehen. Abhängigkeit: Sanktionslisten-Anbindung und bestehende Rollen-/Rechteverwaltung.

## Erledigt

- Firmenverwaltung mit eigenen Stammdaten und Logos, fester Gesellschaftszuordnung je LC und Word-Vorlage; Firmenangaben in Word und Standard-PDFs

- Firmenbezogene Word-Vorlagen mit expliziter Vorlagenfirma je LC-Akte und allgemeiner Ersatzvorlage; vollständige Gesellschaftsverwaltung weiterhin geplant

- Vorlagenverwaltung für Word-Dokumente
- Handelsrechnung und Packliste gegen die gültige LC-Fassung einschließlich MT707-Änderungen prüfen
- Audit-Protokoll mit Vorher-/Nachher-Werten, Exportereignissen und Aktenchronik
- Dokumentenerstellung für Ursprungszeugnis, Begünstigtenzertifikat und Qualitäts-/Analysezertifikat
- Zentrale Firmenstammdaten einschließlich Logo, Register-, Steuer- und Bankdaten
- Strukturierte Positions- und Packtabellen für Handelsrechnung und Packliste
- Versionierte Dokumententwürfe mit Prüfung, Finalisierung und gesperrter Endfassung
- Entwurfsprüfung gegen LC-Betrag, Laufzeit, Warenbeschreibung und Packdaten
- Vier-Augen-Freigabe für Dokumententwürfe mit getrenntem Ersteller und Freigeber
- Optionale TOTP-Zwei-Faktor-Anmeldung mit Authenticator-App und Notfallcodes
- Transport-, Ursprungsland- und Incoterm-Prüfungen mit dokumentübergreifender Widerspruchserkennung
- Batch-Upload für Einzeldateien und ZIP-Archive mit Typkorrektur, Limits und Ergebnisübersicht
- Vererbbare, administrierbare Rollen mit granularen Rechten ([GitHub #3](https://github.com/mascho44/corporate-LC_Manager/issues/3))
- SMTP-E-Mail-Versand aus der LC-Akte mit Anhängen, eigenem Recht, Versandhistorie und Audit-Protokoll ([GitHub #2](https://github.com/mascho44/corporate-LC_Manager/issues/2))
- Provider-unabhängige MQ-Abstraktion mit persistenter Outbox, Wiederholungsstrategie und lokalem Adapter ([GitHub #1](https://github.com/mascho44/corporate-LC_Manager/issues/1))
- Zentrale Outbox-Anbindung aller relevanten LC-, Dokument-, Training-, SWIFT- und E-Mail-Geschäftsereignisse

## Später

- EBICS-Anbindung für den sicheren Bankaustausch
- Multiple Entities: mehrere Gesellschaften oder Mandanten mit getrennten Akten, Benutzern, Rollen und Einstellungen
- Weitere SWIFT-Profile, insbesondere MT767
- Kerberos-Anbindung für Enterprise-SSO mit Rollenabbildung und lokalem Notfall-Adminzugang
