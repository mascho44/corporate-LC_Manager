#!/bin/bash
set -e

cd "$(dirname "$0")"

if ! command -v java >/dev/null 2>&1; then
  echo "Java 17 oder neuer fehlt. Download: https://adoptium.net/temurin/releases/"
  read -r -p "Drücke Enter zum Beenden."
  exit 1
fi

if ! command -v mvn >/dev/null 2>&1; then
  echo "Maven fehlt. Mit Homebrew installieren: brew install maven"
  read -r -p "Drücke Enter zum Beenden."
  exit 1
fi

echo "Baue und teste Corporate LC Manager ..."
mvn clean package

echo "Starte Anwendung mit lokalem H2-Profil ..."
java -jar target/corporate-lc-manager-0.5.0-SNAPSHOT.jar --spring.profiles.active=local &
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

echo "Bitte http://localhost:8080 manuell öffnen."
wait "$app_pid"
