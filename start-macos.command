#!/bin/bash
set -e

cd "$(dirname "$0")"

if ! command -v java >/dev/null 2>&1; then
  echo "Java wurde nicht gefunden."
  echo "Bitte Java 17 oder neuer installieren: https://adoptium.net/temurin/releases/"
  read -r -p "Drücke Enter zum Beenden."
  exit 1
fi

java_version=$(java -version 2>&1 | head -n 1)
echo "Starte Corporate LC Manager mit $java_version"
echo "Die Anwendung öffnet sich gleich unter http://localhost:8080"
echo "Zum Beenden dieses Fenster schließen oder Ctrl+C drücken."
echo

java -jar corporate-lc-manager.jar --spring.profiles.active=local &
app_pid=$!

stop_app() {
  kill "$app_pid" 2>/dev/null || true
}
trap stop_app EXIT INT TERM

attempt=0
while [ "$attempt" -lt 30 ]; do
  if curl -fsS http://127.0.0.1:8080/ >/dev/null 2>&1; then
    open http://localhost:8080
    wait "$app_pid"
    exit $?
  fi
  if ! kill -0 "$app_pid" 2>/dev/null; then
    echo "Die Anwendung konnte nicht gestartet werden."
    read -r -p "Drücke Enter zum Beenden."
    exit 1
  fi
  attempt=$((attempt + 1))
  sleep 1
done

echo "Der Start dauert unerwartet lange. Öffne http://localhost:8080 bitte manuell."
wait "$app_pid"
