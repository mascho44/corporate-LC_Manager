package de.ostms.lc.tenant.domain;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;

/** Server-owned request scope. Bootstrap jobs still use the sole default tenant. */
public final class TenantContext {
 private static final ThreadLocal<UUID> CURRENT=new ThreadLocal<>();
 private TenantContext(){}
 public static UUID currentId(){var id=CURRENT.get();return id==null?Tenant.DEFAULT_ID:id;}
 public static Scope open(UUID id){if(id==null)throw new AccessDeniedException("Tenant context is missing.");var previous=CURRENT.get();CURRENT.set(id);return new Scope(previous);}
 public static void require(UUID owner){if(!currentId().equals(owner))throw new AccessDeniedException("Resource belongs to another tenant.");}
 public static final class Scope implements AutoCloseable {
  private final UUID previous;private boolean closed;
  private Scope(UUID previous){this.previous=previous;}
  public void close(){if(closed)return;closed=true;if(previous==null)CURRENT.remove();else CURRENT.set(previous);}
 }
}
