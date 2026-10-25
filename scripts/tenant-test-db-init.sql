-- Disposable test database only: restricted runtime role like production (docs/datenbank-rollen.md), so the acceptance run exercises row-level security.
CREATE ROLE lcmanager_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT PASSWORD 'SyntheticAppOnly';
GRANT CONNECT ON DATABASE lcmanager TO lcmanager_app;
GRANT USAGE ON SCHEMA public TO lcmanager_app;
ALTER DEFAULT PRIVILEGES FOR ROLE lcmanager IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO lcmanager_app;
ALTER DEFAULT PRIVILEGES FOR ROLE lcmanager IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO lcmanager_app;
-- Kontrollierte Mitgliedschaftsfunktionen (V64/V65) sind fuer PUBLIC gesperrt; neue Funktionen des Besitzers sollen der App-Rolle gehoeren.
ALTER DEFAULT PRIVILEGES FOR ROLE lcmanager IN SCHEMA public GRANT EXECUTE ON FUNCTIONS TO lcmanager_app;
