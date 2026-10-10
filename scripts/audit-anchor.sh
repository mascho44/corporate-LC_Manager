#!/usr/bin/env bash
# Legt den Kopf der Audit-Hash-Kette (je Mandant) ausserhalb der Datenbank ab.
#
# Warum: Die Kette in der Datenbank erkennt nachtraeglich veraenderte oder geloeschte Eintraege, aber nicht, dass jemand mit
# DB-Administratorrechten die ganze Kette neu schreibt. Ein ausserhalb gespeicherter Stand (Sequenznummer + Hash) macht das sichtbar.
#
# Aufruf (z. B. stuendlich per Cron):  scripts/audit-anchor.sh
#          Anker-Datei selbst pruefen:  scripts/audit-anchor.sh --check-log
# Rueckgabe: 0 = alles in Ordnung, 1 = Auffaelligkeit (Kette defekt, Rueckschritt, geaenderter Hash, Datei manipuliert), 2 = technischer Fehler.
#
# Umgebung:
#   AUDIT_ANCHOR_FILE         Zieldatei (Standard: $HOME/audit-anchors/anchors.log)
#   AUDIT_ANCHOR_SOURCE_CMD   Befehl, der je Mandant eine Zeile "code|ok|geprueft|kopf_seq|kopf_hash|unverkettet|grund" ausgibt
#                             (Standard: psql im Produktions-Container)
#   RESTIC_REPOSITORY / RESTIC_PASSWORD_FILE   wenn gesetzt, wird die Datei zusaetzlich verschluesselt extern gesichert
set -Eeuo pipefail
umask 077

APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANCHOR_FILE="${AUDIT_ANCHOR_FILE:-$HOME/audit-anchors/anchors.log}"
ZERO="$(printf '0%.0s' $(seq 1 64))"

sha() { if command -v sha256sum >/dev/null 2>&1; then printf '%s' "$1" | sha256sum | cut -d' ' -f1; else printf '%s' "$1" | shasum -a 256 | cut -d' ' -f1; fi; }

# Jede Zeile endet mit dem SHA-256 der vorherigen Zeile: nachtraegliche Aenderungen an der Datei fallen auf.
check_log() {
  [[ -f "$ANCHOR_FILE" ]] || { echo "Keine Anker-Datei: $ANCHOR_FILE" >&2; return 2; }
  local prev="$ZERO" line n=0 stored
  while IFS= read -r line || [[ -n "$line" ]]; do
    n=$((n + 1))
    stored="${line##*|}"
    if [[ "$stored" != "$prev" ]]; then
      echo "ALARM: Anker-Datei veraendert oder gekuerzt (Zeile $n)." >&2
      return 1
    fi
    prev="$(sha "$line")"
  done < "$ANCHOR_FILE"
  echo "Anker-Datei in Ordnung ($n Zeilen)."
}

if [[ "${1:-}" == "--check-log" ]]; then check_log; exit $?; fi

fetch() {
  if [[ -n "${AUDIT_ANCHOR_SOURCE_CMD:-}" ]]; then bash -c "$AUDIT_ANCHOR_SOURCE_CMD"; return; fi
  docker compose --env-file "$APP_DIR/.env" -f "$APP_DIR/docker-compose.prod.yml" exec -T postgres \
    psql -U lcmanager -d lcmanager -At -F '|' -c \
    "select t.code, v.ok, v.checked, coalesce(v.head_seq,0), coalesce(v.head_hash,''), v.unchained, replace(coalesce(v.reason,''),'|','/') from tenant t, lateral verify_audit_chain(t.id) v order by t.code"
}

mkdir -p "$(dirname "$ANCHOR_FILE")"
touch "$ANCHOR_FILE"
chmod 600 "$ANCHOR_FILE"

if [[ -s "$ANCHOR_FILE" ]]; then check_log >/dev/null || { check_log >&2 || true; exit 1; }; fi

rows="$(fetch)" || { echo "Fehler: Stand der Audit-Kette konnte nicht gelesen werden." >&2; exit 2; }
[[ -n "$rows" ]] || { echo "Fehler: keine Mandanten gefunden." >&2; exit 2; }

status=0
prev_line="$(tail -n 1 "$ANCHOR_FILE")"
prev_hash="$ZERO"
[[ -n "$prev_line" ]] && prev_hash="$(sha "$prev_line")"
now="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

while IFS='|' read -r code ok checked seq hash unchained reason; do
  [[ -n "$code" ]] || continue
  state="OK"
  # Letzter bekannter Stand dieses Mandanten aus der Datei
  last="$(awk -F'|' -v c="$code" '$2==c && $3=="OK" {l=$0} END{print l}' "$ANCHOR_FILE")"
  if [[ "$ok" != "t" && "$ok" != "true" ]]; then
    state="BAD"; status=1
    echo "ALARM: Audit-Kette von '$code' ist defekt (${reason:-ohne Angabe})." >&2
  elif [[ -n "$last" ]]; then
    last_seq="$(echo "$last" | cut -d'|' -f5)"; last_hash="$(echo "$last" | cut -d'|' -f6)"
    if (( seq < last_seq )); then
      state="BAD"; status=1
      echo "ALARM: Audit-Kette von '$code' ist zurueckgegangen (Sequenz $seq statt mindestens $last_seq)." >&2
    elif (( seq == last_seq )) && [[ "$hash" != "$last_hash" ]]; then
      state="BAD"; status=1
      echo "ALARM: Kopf-Hash von '$code' bei Sequenz $seq weicht vom gesicherten Stand ab." >&2
    fi
  fi
  line="$now|$code|$state|$checked|$seq|$hash|$unchained|$prev_hash"
  echo "$line" >> "$ANCHOR_FILE"
  prev_hash="$(sha "$line")"
  echo "$code: $state, Sequenz $seq"
done <<< "$rows"

# Optional: verschluesselte externe Kopie der Anker-Datei (gleiche Restic-Einrichtung wie die Datenbanksicherung).
if [[ -n "${RESTIC_REPOSITORY:-}" && -n "${RESTIC_PASSWORD_FILE:-}" ]] && command -v restic >/dev/null 2>&1; then
  restic backup --tag corporate-lc-manager-audit-anchor "$ANCHOR_FILE" >/dev/null || { echo "Warnung: externe Sicherung der Anker-Datei fehlgeschlagen." >&2; status=2; }
fi
exit "$status"
