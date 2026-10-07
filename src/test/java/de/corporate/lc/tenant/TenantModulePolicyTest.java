package de.corporate.lc.tenant;
import de.corporate.lc.tenant.domain.Tenant;
import de.corporate.lc.tenant.service.TenantModulePolicy;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class TenantModulePolicyTest {
 @Test void sharedFunctionsRemainAvailable(){for(var t:new Tenant[]{new Tenant("bank","Bank","en",true,false),new Tenant("corporate","Corporate","en",false,true)})for(String path:new String[]{"/api/lcs","/api/inbox","/api/training","/api/lcs/123/document-checks","/api/lcs/123/documents"})assertThat(TenantModulePolicy.allowed(t,path,"GET",null)).isTrue();}
 @Test void bankCannotGenerateOrManageTemplates(){var t=new Tenant();for(String path:new String[]{"/api/document-templates","/api/company-profile","/api/lcs/123/generated-documents/docx","/api/lcs/123/document-drafts/456/generate"})assertThat(TenantModulePolicy.allowed(t,path,"POST",null)).isFalse();assertThat(TenantModulePolicy.allowed(t,"/api/lcs/123/document-drafts/456/status","PUT","FINAL")).isTrue();assertThat(TenantModulePolicy.allowed(t,"/api/lcs/123/document-drafts/456/status","PUT","SUBMITTED")).isFalse();}
 @Test void corporateCannotUseBankFunctions(){var t=new Tenant("corp","Corporate","en",false,true);assertThat(TenantModulePolicy.allowed(t,"/api/training/advising/123/new-case","POST",null)).isFalse();assertThat(TenantModulePolicy.allowed(t,"/api/settings/approval-thresholds","GET",null)).isFalse();assertThat(TenantModulePolicy.allowed(t,"/api/lcs/123/document-drafts/456/status","PUT","FINAL")).isFalse();assertThat(TenantModulePolicy.allowed(t,"/api/lcs/123/document-drafts/456/status","PUT","SUBMITTED")).isTrue();}
 @Test void combinedAllowsBothAndSuspensionDeniesAll(){var t=new Tenant("both","Combined","en",true,true);assertThat(TenantModulePolicy.allowed(t,"/api/training/advising","POST",null)).isTrue();assertThat(TenantModulePolicy.allowed(t,"/api/document-templates","GET",null)).isTrue();t.setActive(false);assertThat(TenantModulePolicy.allowed(t,"/api/lcs","GET",null)).isFalse();assertThatThrownBy(()->t.updateProfile(false,false)).isInstanceOf(IllegalArgumentException.class);}
}
