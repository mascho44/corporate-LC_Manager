package de.corporate.lc.company.repository;import de.corporate.lc.company.domain.CompanyProfile;import org.springframework.data.jpa.repository.JpaRepository;public interface CompanyProfileRepository extends JpaRepository<CompanyProfile,Integer>{
@org.springframework.data.jpa.repository.Query("select c from CompanyProfile c where c.tenantId=:#{T(de.corporate.lc.tenant.domain.TenantContext).currentId()} order by c.id")
java.util.List<CompanyProfile> findAll();
@org.springframework.data.jpa.repository.Query("select c from CompanyProfile c where c.id=:id and c.tenantId=:#{T(de.corporate.lc.tenant.domain.TenantContext).currentId()}")
java.util.Optional<CompanyProfile> findById(@org.springframework.data.repository.query.Param("id") Integer id);
default java.util.Optional<CompanyProfile> findDefault(){return findAll().stream().findFirst();}
@org.springframework.data.jpa.repository.Query(value="select nextval(\'company_profile_id_seq\')",nativeQuery=true) Integer nextId();}
