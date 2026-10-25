# Teams und Gruppeninbox

**Teams** gelten je Mandant und werden von Nutzerverwaltern (Recht `USER_MANAGE`) unter *Gruppeninbox → Teams verwalten* angelegt:
Name, Beschreibung, Mitglieder (aktive Nutzer des Mandanten), Aktiv-Schalter. Ein Nutzer kann in mehreren Teams sein. Teams werden nicht gelöscht,
sondern auf *inaktiv* gesetzt; inaktive Teams zeigen keine Aufträge mehr und erhalten keine neuen.

**Teamauftrag:** eine Aufgabe an einer Akte, die einem Team statt einer Person zugewiesen ist (*Neuer Teamauftrag* in der Gruppeninbox).
Wird zusätzlich eine Person angegeben, muss sie im Team sein; der Auftrag gilt dann sofort als übernommen.

**Gruppeninbox** (für Mitglieder; Menüpunkt mit Zahl der offenen Aufträge):
- *Zur Übernahme*: noch niemand zuständig. **Übernehmen** setzt die Person als Bearbeiter, atomar: übernehmen zwei gleichzeitig, bekommt nur eine den Auftrag.
- *In meiner Bearbeitung*: **Erledigt** schließt den Auftrag, **Zurückgeben** legt ihn zurück in die Inbox (nur die übernehmende Person oder ein Nutzerverwalter).
- *Bei Kolleginnen und Kollegen*: Sicht auf übernommene Aufträge des Teams, nur lesend.

Übernehmen und Zurückgeben brauchen das Recht `LC_EDIT`; alle Aktionen stehen im Audit-Protokoll (`TEAM_CREATED`, `TEAM_UPDATED`, `TASK_CLAIMED`, `TASK_RELEASED`).

## Workflows
Ein Workflow läuft als Kette von Teamaufträgen: Beim Start entsteht der erste Schritt in der Gruppeninbox des gewählten Teams; wird er erledigt,
entsteht der nächste. Gestartet wird in der Gruppeninbox unter *Workflow starten* (Recht `LC_EDIT`); der Verlauf steht in der Akte unter *Workflows*.

| Vorlage | Schritte |
|---|---|
| Neue Akte prüfen | Akte erfassen und Stammdaten prüfen → Inhaltlich prüfen → Freigeben (Vier-Augen) |
| Änderung (MT707) bearbeiten | Änderung prüfen → Änderung bestätigen |
| Garantie bearbeiten | Garantie erfassen → Garantie prüfen → Freigeben (Vier-Augen) |
| Dokumentenprüfung | Dokumente prüfen → Abweichungen klären → Prüfung freigeben (Vier-Augen) |

- **Vier-Augen:** Ein solcher Schritt kann nicht von der Person übernommen oder abgeschlossen werden, die den Schritt davor erledigt hat.
  Optional lässt sich ein eigenes **Freigabeteam** für diese Schritte wählen.
- Jeder Schritt hat eine Frist (1–3 Tage ab Aktivierung). Erledigt wird nur durch Teammitglieder; hat jemand den Schritt übernommen, nur durch diese Person.
- Je Akte und Vorlage läuft höchstens ein Workflow gleichzeitig. **Abbrechen** entfernt den offenen Schritt; erledigte Schritte bleiben als Verlauf.
- Workflow-Schritte lassen sich nicht wieder öffnen oder einzeln löschen. Audit: `WORKFLOW_STARTED`, `WORKFLOW_CANCELLED`.

## Automatische Aufträge
Unter *Gruppeninbox → Automatische Aufträge* (Recht `USER_MANAGE`) lässt sich je Mandant und Auslöser einschalten, dass Aufträge für ein Team entstehen (Standard: aus):

| Auslöser | Auftrag | schließt sich, wenn … |
|---|---|---|
| Neue EBICS-Nachricht | „Nachricht prüfen und importieren: MT… Referenz“ (fällig +1 Tag) | die Nachricht importiert oder verworfen wurde |
| Neues Dokument im Posteingang | „Dokument zuordnen: Dateiname“ (fällig +1 Tag) | das Dokument zugeordnet, aufgeteilt oder gelöscht wurde |
| Frist oder Wiedervorlage | „Frist: Referenz – Ablauf/Wiedervorlage am …“ (fällig am Fristtag) | ein Teammitglied ihn erledigt |

- Fristaufträge entstehen, wenn die Frist innerhalb des **Vorlaufs** (0–60 Tage, Standard 3) liegt; ein stündlicher Lauf prüft alle aktiven Akten (nicht abgeschlossen, nicht abgelaufen).
  Je Akte und Fristdatum entsteht höchstens ein Auftrag, auch wenn er schon erledigt wurde.
- Aufträge zu Nachricht oder Posteingang gehören keiner Akte an (*Öffnen* springt zur EBICS-Seite bzw. zum Posteingang) und erscheinen nicht im Arbeitsvorrat der Akten, sondern nur in der Gruppeninbox.
- Fehler beim Anlegen eines Auftrags stoppen weder den EBICS-Abruf noch den Upload.
- Die Startseite zeigt die Zahl der noch nicht übernommenen Aufträge als Kachel „Gruppeninbox“.
