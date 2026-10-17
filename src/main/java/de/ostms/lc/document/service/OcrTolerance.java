package de.ostms.lc.document.service;

import de.ostms.lc.tenant.repository.TenantRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.UUID;

/** The OCR tolerance of a tenant: minimum word confidence counted as certain; the installation default applies until the tenant sets its own. */
@Service
public class OcrTolerance {
 private final TenantRepository tenants;private final double defaultThreshold;
 public OcrTolerance(TenantRepository tenants,@Value("${lc.ocr.confidence-threshold:0.8}") double defaultThreshold){
  if(!Double.isFinite(defaultThreshold)||defaultThreshold<0||defaultThreshold>1)throw new IllegalArgumentException("Ungültige OCR-Konfidenzschwelle");
  this.tenants=tenants;this.defaultThreshold=defaultThreshold;
 }
 public double defaultThreshold(){return defaultThreshold;}
 /** Threshold of the tenant, the default when none is set or the tenant is unknown. */
 public double forTenant(UUID tenantId){
  if(tenantId==null)return defaultThreshold;
  return tenants.findById(tenantId).map(t->t.getOcrConfidenceThreshold()).orElse(defaultThreshold);
 }
}
