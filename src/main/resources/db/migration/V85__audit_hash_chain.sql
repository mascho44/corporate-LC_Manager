-- Tamper evidence for the audit log (BAIT/DORA): a SHA-256 hash chain per tenant, maintained by the database
-- itself so no application path can skip it. Rows written before this migration stay unchained (NULL);
-- an anchor event per tenant marks where the chain starts.
ALTER TABLE audit_event ADD COLUMN chain_seq bigint;
ALTER TABLE audit_event ADD COLUMN prev_hash char(64);
ALTER TABLE audit_event ADD COLUMN entry_hash char(64);
CREATE UNIQUE INDEX uq_audit_event_chain ON audit_event (tenant_id, chain_seq) WHERE chain_seq IS NOT NULL;

-- Single source of truth for the hash of one row (used by the trigger and by the verification function).
-- Timestamps are rendered explicitly so the result does not depend on the session time zone.
CREATE FUNCTION audit_event_hash(a audit_event) RETURNS char(64) LANGUAGE sql IMMUTABLE AS $$
 SELECT encode(sha256(convert_to(a.prev_hash || jsonb_build_array(
   a.chain_seq, a.id::text, a.tenant_id::text, a.username, a.action, a.entity_type, a.entity_id, a.details, a.successful,
   a.previous_value, a.new_value, a.ip_address,
   to_char(a.occurred_at,'YYYY-MM-DD"T"HH24:MI:SS.US'),
   to_char(a.occurred_at_utc AT TIME ZONE 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US'),
   a.actor_roles, a.session_ref, a.request_id, a.user_agent, a.failure_reason)::text,'UTF8')),'hex')::char(64)
$$;

CREATE FUNCTION audit_event_chain() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE last_seq bigint; last_hash char(64);
BEGIN
 -- Serialize writers per tenant until their transaction ends so the chain has no forks or gaps.
 PERFORM pg_advisory_xact_lock(hashtextextended('audit_chain:'||NEW.tenant_id::text,0));
 SELECT chain_seq, entry_hash INTO last_seq, last_hash FROM audit_event
  WHERE tenant_id = NEW.tenant_id AND chain_seq IS NOT NULL ORDER BY chain_seq DESC LIMIT 1;
 IF last_seq IS NULL THEN NEW.chain_seq := 1; NEW.prev_hash := repeat('0',64);
 ELSE NEW.chain_seq := last_seq + 1; NEW.prev_hash := last_hash; END IF;
 NEW.entry_hash := audit_event_hash(NEW);
 RETURN NEW;
END $$;
CREATE TRIGGER trg_audit_event_chain BEFORE INSERT ON audit_event FOR EACH ROW EXECUTE FUNCTION audit_event_chain();

-- Result: ok, number of chained rows checked, first broken sequence number (NULL if intact), head sequence and head hash,
-- and the number of older rows that are not part of the chain.
CREATE FUNCTION verify_audit_chain(p_tenant uuid)
 RETURNS TABLE(ok boolean, checked bigint, first_bad_seq bigint, head_seq bigint, head_hash text, unchained bigint, reason text)
 LANGUAGE plpgsql STABLE AS $$
DECLARE r audit_event; expected_seq bigint := 1; expected_prev char(64) := repeat('0',64); n bigint := 0;
BEGIN
 FOR r IN SELECT * FROM audit_event WHERE tenant_id = p_tenant AND chain_seq IS NOT NULL ORDER BY chain_seq LOOP
  n := n + 1;
  IF r.chain_seq <> expected_seq THEN
   RETURN QUERY SELECT false, n, expected_seq, NULL::bigint, NULL::text, 0::bigint, 'Luecke in der Folge'::text; RETURN;
  END IF;
  IF r.prev_hash <> expected_prev THEN
   RETURN QUERY SELECT false, n, r.chain_seq, NULL::bigint, NULL::text, 0::bigint, 'Verkettung unterbrochen'::text; RETURN;
  END IF;
  IF r.entry_hash <> audit_event_hash(r) THEN
   RETURN QUERY SELECT false, n, r.chain_seq, NULL::bigint, NULL::text, 0::bigint, 'Inhalt veraendert'::text; RETURN;
  END IF;
  expected_seq := expected_seq + 1; expected_prev := r.entry_hash;
 END LOOP;
 RETURN QUERY SELECT true, n, NULL::bigint, CASE WHEN n=0 THEN NULL ELSE expected_seq-1 END,
  CASE WHEN n=0 THEN NULL ELSE expected_prev::text END,
  (SELECT count(*) FROM audit_event WHERE tenant_id = p_tenant AND chain_seq IS NULL), NULL::text;
END $$;

-- Anchor event per tenant: documents where the chain starts and how many earlier rows are unchained.
INSERT INTO audit_event(id, username, action, entity_type, details, successful, occurred_at, occurred_at_utc, tenant_id)
 SELECT gen_random_uuid(), 'system', 'AUDIT_CHAIN_STARTED', 'AUDIT',
        'Hash-Kette gestartet; fruehere Eintraege (' || (SELECT count(*) FROM audit_event e WHERE e.tenant_id = t.id) || ') sind nicht verkettet.',
        true, now(), now(), t.id FROM tenant t;
