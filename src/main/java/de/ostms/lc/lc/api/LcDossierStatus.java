package de.ostms.lc.lc.api;

import java.util.UUID;

public record LcDossierStatus(UUID lcId,String status,long documents,long discrepancies,long warnings,long passed) {}
