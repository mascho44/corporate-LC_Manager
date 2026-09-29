# Corporate LC Manager – Roadmap

## Als Nächstes

- Ereignisse schrittweise an die Integrations-Outbox anbinden und bei Bedarf einen RabbitMQ-Adapter aktivieren

## Erledigt

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

## Später

- EBICS-Anbindung für den sicheren Bankaustausch
- Multiple Entities: mehrere Gesellschaften oder Mandanten mit getrennten Akten, Benutzern, Rollen und Einstellungen
- Weitere SWIFT-Profile, insbesondere MT767
- Kerberos-Anbindung für Enterprise-SSO mit Rollenabbildung und lokalem Notfall-Adminzugang
