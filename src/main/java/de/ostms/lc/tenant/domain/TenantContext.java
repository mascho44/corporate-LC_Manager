package de.ostms.lc.tenant.domain;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;

/** Server-owned request scope. Bootstrap jobs still use the sole default tenant. */
public final class TenantContext {
 private static final ThreadLocal<UUID> CURRENT=new ThreadLocal<>();
 private static final ThreadLocal<Boolean> PLATFORM=new ThreadLocal<>();
 private static volatile Runnable changeListener;
 private TenantContext(){}
 /** Lets the database layer follow tenant switches made inside an already open transaction. */
 public static void onChange(Runnable listener){changeListener=listener;}
 /** True only inside {@link #openPlatform()}: explicit platform code that must see rows of every tenant (database policies honour it). */
 public static boolean platform(){return Boolean.TRUE.equals(PLATFORM.get());}
 /** Narrow, deliberate scope for platform operations (overview, tenant provisioning, token lookup before the tenant is known). */
 public static Scope openPlatform(){var previous=PLATFORM.get();PLATFORM.set(Boolean.TRUE);try{changed(true);}catch(RuntimeException failure){restorePlatform(previous);throw failure;}return new Scope(null,true,previous);}
 private static void restorePlatform(Boolean previous){if(previous==null)PLATFORM.remove();else PLATFORM.set(previous);}
 private static void changed(boolean strict){var listener=changeListener;if(listener==null)return;try{listener.run();}catch(RuntimeException failure){if(strict)throw failure;}}
 public static UUID currentId(){var id=CURRENT.get();return id==null?Tenant.DEFAULT_ID:id;}
 public static Scope open(UUID id){if(id==null)throw new AccessDeniedException("Tenant context is missing.");var previous=CURRENT.get();CURRENT.set(id);try{changed(true);}catch(RuntimeException failure){if(previous==null)CURRENT.remove();else CURRENT.set(previous);throw failure;}return new Scope(previous,false,null);}
 public static void require(UUID owner){if(!currentId().equals(owner))throw new AccessDeniedException("Resource belongs to another tenant.");}
 public static final class Scope implements AutoCloseable {
  private final UUID previous;private final boolean platformScope;private final Boolean previousPlatform;private boolean closed;
  private Scope(UUID previous,boolean platformScope,Boolean previousPlatform){this.previous=previous;this.platformScope=platformScope;this.previousPlatform=previousPlatform;}
  public void close(){if(closed)return;closed=true;if(platformScope)restorePlatform(previousPlatform);else if(previous==null)CURRENT.remove();else CURRENT.set(previous);changed(false);}
 }
}
