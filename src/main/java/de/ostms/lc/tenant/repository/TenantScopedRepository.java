package de.ostms.lc.tenant.repository;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityNotFoundException;
import java.util.*;

/** Business repositories only: authentication and provisioning have separate boundaries. */
@NoRepositoryBean
public interface TenantScopedRepository<T, ID> extends JpaRepository<T, ID> {
 String OWNED = "e.tenantId=:#{T(de.ostms.lc.tenant.domain.TenantContext).currentId()}";

 @Override @Query("select e from #{#entityName} e where " + OWNED)
 List<T> findAll();
 @Override @Query("select e from #{#entityName} e where " + OWNED)
 List<T> findAll(Sort sort);
 @Override @Query(value="select e from #{#entityName} e where " + OWNED,
                  countQuery="select count(e) from #{#entityName} e where " + OWNED)
 Page<T> findAll(Pageable pageable);
 @Override @Query("select count(e) from #{#entityName} e where " + OWNED)
 long count();

 // Each concrete repository supplies a scoped ID lookup, including composite IDs.
 @Override default Optional<T> findById(ID id){throw new UnsupportedOperationException("Concrete repositories must provide a tenant-scoped ID lookup.");}
 @Override @Transactional(readOnly=true)
 default List<T> findAllById(Iterable<ID> ids) {
  Objects.requireNonNull(ids);Set<ID> unique=new LinkedHashSet<>();ids.forEach(unique::add);
  return unique.stream().map(this::findById).flatMap(Optional::stream).toList();
 }
 @Override @Transactional(readOnly=true)
 default boolean existsById(ID id){return findById(id).isPresent();}
 @Override @Transactional(readOnly=true)
 default T getReferenceById(ID id){return findById(id).orElseThrow(()->new EntityNotFoundException("Resource not found."));}
 @Override @Deprecated @Transactional(readOnly=true)
 default T getById(ID id){return getReferenceById(id);}
 @Override @Deprecated @Transactional(readOnly=true)
 default T getOne(ID id){return getReferenceById(id);}

 @Override @Transactional
 default void deleteById(ID id){findById(id).ifPresent(this::delete);}
 @Override @Transactional
 default void deleteAllById(Iterable<? extends ID> ids){ids.forEach(this::deleteById);}
 @Override @Transactional
 default void deleteAll(){findAll().forEach(this::delete);}

 // JPQL batch deletion bypasses lifecycle ownership and append-only safeguards.
 @Override default void deleteAllInBatch(){throw batchDeletionDisabled();}
 @Override default void deleteAllInBatch(Iterable<T> entities){throw batchDeletionDisabled();}
 @Override default void deleteAllByIdInBatch(Iterable<ID> ids){throw batchDeletionDisabled();}
 @Override @Deprecated default void deleteInBatch(Iterable<T> entities){throw batchDeletionDisabled();}
 private static UnsupportedOperationException batchDeletionDisabled(){return new UnsupportedOperationException("Use a tenant-scoped deletion service with lifecycle checks.");}

 // Query-by-example and fluent queries must not silently reopen unfiltered reads.
 @Override default <S extends T> Optional<S> findOne(Example<S> example){throw exampleQueriesDisabled();}
 @Override default <S extends T> List<S> findAll(Example<S> example){throw exampleQueriesDisabled();}
 @Override default <S extends T> List<S> findAll(Example<S> example,Sort sort){throw exampleQueriesDisabled();}
 @Override default <S extends T> Page<S> findAll(Example<S> example,Pageable pageable){throw exampleQueriesDisabled();}
 @Override default <S extends T> long count(Example<S> example){throw exampleQueriesDisabled();}
 @Override default <S extends T> boolean exists(Example<S> example){throw exampleQueriesDisabled();}
 @Override default <S extends T,R> R findBy(Example<S> example,java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>,R> queryFunction){throw exampleQueriesDisabled();}
 private static UnsupportedOperationException exampleQueriesDisabled(){return new UnsupportedOperationException("Use tenant-scoped explicit queries instead of query-by-example.");}
}
