package com.portfolio.agent.answer.composition.adapter.model;

import com.portfolio.agent.answer.composition.domain.MaterialKind;
import com.portfolio.agent.answer.domain.ModelProviderKind;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "portfolio.model-expression")
public final class PortfolioExpressionProperties {
    private boolean enabled;
    private ModelProviderKind provider = ModelProviderKind.DEEPSEEK_V4_FLASH;
    private String policyVersion = "p4-expression-policy-v1";
    private String inputSchemaVersion = "portfolio-expression-input.v1";
    private String draftSchemaVersion = "portfolio-expression-draft.v1";
    private Set<MaterialKind> allowedMaterialKinds = new LinkedHashSet<>(Set.of(MaterialKind.FACT));
    private Duration timeout = Duration.ofSeconds(4);
    private int maxOutputTokens = 1600;
    private boolean externalPublicDataPolicyApproved;
    private String deepseekApiKey = "";
    private String glmApiKey = "";
    public boolean isEnabled(){return enabled;} public void setEnabled(boolean value){enabled=value;}
    public ModelProviderKind getProvider(){return provider;} public void setProvider(ModelProviderKind value){provider=value;}
    public String getPolicyVersion(){return policyVersion;} public void setPolicyVersion(String value){policyVersion=value;}
    public String getInputSchemaVersion(){return inputSchemaVersion;} public void setInputSchemaVersion(String value){inputSchemaVersion=value;}
    public String getDraftSchemaVersion(){return draftSchemaVersion;} public void setDraftSchemaVersion(String value){draftSchemaVersion=value;}
    public Set<MaterialKind> getAllowedMaterialKinds(){return Set.copyOf(allowedMaterialKinds);} public void setAllowedMaterialKinds(Set<MaterialKind> value){allowedMaterialKinds=new LinkedHashSet<>(value);}
    public Duration getTimeout(){return timeout;} public void setTimeout(Duration value){timeout=value;}
    public int getMaxOutputTokens(){return maxOutputTokens;} public void setMaxOutputTokens(int value){maxOutputTokens=value;}
    public boolean isExternalPublicDataPolicyApproved(){return externalPublicDataPolicyApproved;} public void setExternalPublicDataPolicyApproved(boolean value){externalPublicDataPolicyApproved=value;}
    public String getDeepseekApiKey(){return deepseekApiKey;} public void setDeepseekApiKey(String value){deepseekApiKey=value;}
    public String getGlmApiKey(){return glmApiKey;} public void setGlmApiKey(String value){glmApiKey=value;}
    public String selectedApiKey(){
        if (provider == ModelProviderKind.GLM_4_7) return glmApiKey == null ? "" : glmApiKey.strip();
        return deepseekApiKey == null ? "" : deepseekApiKey.strip();
    }
}
