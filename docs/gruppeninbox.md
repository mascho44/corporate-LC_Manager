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

## Geplant
Workflow-Vorlagen mit Schritten, Vier-Augen-Freigabe und Fristen; automatische Aufträge aus EBICS-Nachrichten, Posteingang und Fristen.
