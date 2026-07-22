package com.portfolio.agent.answer.adapter.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.portfolio.agent.answer.domain.AnswerAchievementStatus;
import com.portfolio.agent.answer.domain.AnswerClaimCategory;
import com.portfolio.agent.answer.domain.AnswerClaimVerificationStatus;
import com.portfolio.agent.answer.domain.AnswerContributionType;
import com.portfolio.agent.answer.domain.AnswerMateriality;
import com.portfolio.agent.answer.domain.AnswerPlan;
import com.portfolio.agent.answer.domain.AnswerPlanClaim;
import com.portfolio.agent.answer.domain.AnswerPlanEvidence;
import com.portfolio.agent.answer.domain.AnswerPlanSection;
import com.portfolio.agent.answer.domain.AnswerSectionType;
import com.portfolio.agent.answer.domain.AnswerVerificationBasis;
import com.portfolio.agent.answer.domain.ExpressionPolicy;
import com.portfolio.agent.answer.domain.ExpressionTone;
import com.portfolio.agent.answer.domain.ModelExpressionFailureCode;
import com.portfolio.agent.answer.domain.ModelExpressionRequest;
import com.portfolio.agent.answer.domain.ModelExpressionResult;
import com.portfolio.agent.answer.domain.ModelProviderKind;
import com.portfolio.agent.answer.dto.request.AudienceRole;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;

import java.net.http.HttpTimeoutException;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiCompatibleModelExpressionAdapterTest {

    private static final String VISITOR_SENTINEL = "private visitor wording must stay local";

    @Test
    void callsDeepSeekV4FlashOnceWithOnlyTheWhitelistPlan() {
        Fixture fixture = fixture(ModelProviderKind.DEEPSEEK_V4_FLASH);
        fixture.server().expect(ExpectedCount.once(),
                        requestTo("https://api.deepseek.com/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(content().string(allOf(
                        containsString("deepseek-v4-flash"),
                        containsString("json_object"),
                        containsString("disabled"),
                        containsString("Describe the approved project"),
                        not(containsString(VISITOR_SENTINEL)),
                        not(containsString("requestId")),
                        not(containsString("turnId")),
                        not(containsString("contextEnvelope")),
                        not(containsString("previousContentVersion")),
                        not(containsString("projectSlugs")),
                        not(containsString("referencedClaimIds")),
                        not(containsString("selectedSectionType")),
                        not(containsString("toolPlan")),
                        not(containsString("toolResult")),
                        not(containsString("previousQuestion")),
                        not(containsString("previousAnswer"))
                )))
                .andRespond(withSuccess(providerResponse(validDraftJson()), MediaType.APPLICATION_JSON));

        ModelExpressionResult result = fixture.adapter().express(request());

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.getDraft().getTitle()).isEqualTo("Approved project");
        fixture.server().verify();
    }

    @Test
    void callsGlm47OnceWithTheSameProviderNeutralContract() {
        Fixture fixture = fixture(ModelProviderKind.GLM_4_7);
        fixture.server().expect(ExpectedCount.once(),
                        requestTo("https://open.bigmodel.cn/api/paas/v4/chat/completions"))
                .andExpect(content().string(allOf(
                        containsString("glm-4.7"),
                        containsString("json_object"),
                        containsString("disabled"),
                        not(containsString(VISITOR_SENTINEL))
                )))
                .andRespond(withSuccess(providerResponse(validDraftJson()), MediaType.APPLICATION_JSON));

        ModelExpressionResult result = fixture.adapter().express(request());

        assertThat(result.isSuccessful()).isTrue();
        fixture.server().verify();
    }

    @Test
    void rejectsUnknownDraftFieldsAndDoesNotExposeTheRawResponse() {
        Fixture fixture = fixture(ModelProviderKind.DEEPSEEK_V4_FLASH);
        String invalidDraft = validDraftJson().replace(
                "\"summary\":\"Approved public summary\"",
                "\"summary\":\"Approved public summary\",\"verification\":\"VERIFIED\"");
        fixture.server().expect(ExpectedCount.once(), requestTo(
                        "https://api.deepseek.com/chat/completions"))
                .andRespond(withSuccess(providerResponse(invalidDraft), MediaType.APPLICATION_JSON));

        ModelExpressionResult result = fixture.adapter().express(request());

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.getFailureCode())
                .isEqualTo(ModelExpressionFailureCode.INVALID_RESPONSE);
        assertThat(ModelExpressionResult.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .doesNotContain("rawRequest", "rawResponse", "prompt", "cause");
        fixture.server().verify();
    }

    @Test
    void providerFailureIsReturnedAfterExactlyOneAttempt() {
        Fixture fixture = fixture(ModelProviderKind.GLM_4_7);
        fixture.server().expect(ExpectedCount.once(), requestTo(
                        "https://open.bigmodel.cn/api/paas/v4/chat/completions"))
                .andRespond(withServerError());

        ModelExpressionResult result = fixture.adapter().express(request());

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.getFailureCode())
                .isEqualTo(ModelExpressionFailureCode.PROVIDER_ERROR);
        fixture.server().verify();
    }

    @Test
    void timeoutIsClassifiedWithoutRetryOrRawExceptionExposure() {
        Fixture fixture = fixture(ModelProviderKind.DEEPSEEK_V4_FLASH);
        fixture.server().expect(ExpectedCount.once(), requestTo(
                        "https://api.deepseek.com/chat/completions"))
                .andRespond(request -> {
                    throw new ResourceAccessException(
                            "provider request timed out",
                            new HttpTimeoutException("sensitive-timeout-detail"));
                });

        ModelExpressionResult result = fixture.adapter().express(request());

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.getFailureCode()).isEqualTo(ModelExpressionFailureCode.TIMEOUT);
        fixture.server().verify();
    }

    private Fixture fixture(ModelProviderKind provider) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        ModelProviderDescriptor descriptor = ModelProviderRegistrySnapshot.builtIn()
                .getRequiredDescriptor(provider);
        OpenAiCompatibleModelExpressionAdapter adapter =
                new OpenAiCompatibleModelExpressionAdapter(
                        builder,
                        objectMapper,
                        new ModelPromptFactory(objectMapper),
                        descriptor,
                        "test-key",
                        1200
                );
        return new Fixture(adapter, server);
    }

    private ModelExpressionRequest request() {
        return new ModelExpressionRequest("c1.answer.v1", plan());
    }

    private AnswerPlan plan() {
        return new AnswerPlan(
                "2026-07-21.1",
                "preset-1",
                "Describe the approved project",
                AudienceRole.INTERVIEWER,
                "Approved project",
                "Approved public summary",
                List.of(new AnswerPlanSection(
                        AnswerSectionType.STATUS,
                        "Status",
                        "The approved release is deployed.",
                        List.of("claim-1"),
                        List.of("evidence-1")
                )),
                List.of(new AnswerPlanClaim(
                        "claim-1",
                        AnswerClaimCategory.OUTCOME,
                        "The approved release is deployed.",
                        "Deployment and handoff are complete.",
                        AnswerAchievementStatus.DELIVERED,
                        AnswerContributionType.PRIMARY,
                        AnswerVerificationBasis.EVIDENCE_SUPPORTED,
                        AnswerClaimVerificationStatus.VERIFIED,
                        AnswerMateriality.KEY,
                        List.of("evidence-1")
                )),
                List.of(new AnswerPlanEvidence(
                        "evidence-1",
                        "Release evidence",
                        "DELIVERY_SET",
                        LocalDate.of(2026, 7, 1),
                        LocalDate.of(2026, 7, 20),
                        2,
                        "Approved release notes and deployment record."
                )),
                new ExpressionPolicy(
                        ExpressionTone.CONCISE_TECHNICAL,
                        120, 250, 80, 1000, true, true,
                        List.of(AnswerClaimCategory.OUTCOME)
                )
        );
    }

    private String validDraftJson() {
        return "{\"title\":\"Approved project\","
                + "\"summary\":\"Approved public summary\","
                + "\"sections\":[{"
                + "\"type\":\"STATUS\","
                + "\"title\":\"Status\","
                + "\"content\":\"The approved release is deployed.\","
                + "\"evidenceIds\":[\"evidence-1\"],"
                + "\"claimIds\":[\"claim-1\"]}]}";
    }

    private String providerResponse(String draftJson) {
        String escaped = draftJson.replace("\\", "\\\\").replace("\"", "\\\"");
        return "{\"choices\":[{\"message\":{\"content\":\"" + escaped + "\"}}]}";
    }

    private static final class Fixture {
        private final OpenAiCompatibleModelExpressionAdapter adapter;
        private final MockRestServiceServer server;

        private Fixture(
                OpenAiCompatibleModelExpressionAdapter adapter,
                MockRestServiceServer server
        ) {
            this.adapter = adapter;
            this.server = server;
        }

        private OpenAiCompatibleModelExpressionAdapter adapter() { return adapter; }
        private MockRestServiceServer server() { return server; }
    }
}
