package com.portfolio.agent.infrastructure.model.structured;

import com.portfolio.agent.infrastructure.model.policy.ModelOperation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeneralDraftV3SchemaTest {
    private static final StructuredContractRef CONTRACT = new StructuredContractRef(
            ModelOperation.GENERAL_KNOWLEDGE, "general.draft.v3");
    private final StructuredOutputContractRegistry registry =
            StructuredOutputContractRegistry.standard();

    @Test
    void acceptsExactlyOneDefinitionAndOneMechanism() {
        StructurallyValidatedOutput output = registry.validate(CONTRACT, validDraft());

        assertThat(output.contractRef()).isEqualTo(CONTRACT);
        assertThat(output.jsonTree().path("statements")).hasSize(2);
        assertThat(output.jsonTree().path("caveats")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            """
            {"topic":"依赖注入","statements":[
              {"role":"DEFINITION","text":"定义。","subject":null,"dimension":null,
               "aspects":["DEFINITION"]}],"caveats":[]}
            """,
            """
            {"topic":"依赖注入","statements":[
              {"role":"DEFINITION","text":"定义。","subject":null,"dimension":null,
               "aspects":["DEFINITION"]},
              {"role":"MECHANISM","text":"机制。","subject":null,"dimension":null,
               "aspects":["MECHANISM"]},
              {"role":"MECHANISM","text":"更多机制。","subject":null,"dimension":null,
               "aspects":["MECHANISM"]}],"caveats":[]}
            """,
            """
            {"topic":"依赖注入","statements":[
              {"role":"MECHANISM","text":"定义。","subject":null,"dimension":null,
               "aspects":["DEFINITION"]},
              {"role":"DEFINITION","text":"机制。","subject":null,"dimension":null,
               "aspects":["MECHANISM"]}],"caveats":[]}
            """,
            """
            {"topic":"依赖注入","statements":[
              {"role":"DEFINITION","text":"定义。","subject":"模型回显","dimension":null,
               "aspects":["DEFINITION"]},
              {"role":"MECHANISM","text":"机制。","subject":null,"dimension":null,
               "aspects":["MECHANISM"]}],"caveats":[]}
            """,
            """
            {"topic":"依赖注入","statements":[
              {"role":"DEFINITION","text":"定义。","subject":null,"dimension":null,
               "aspects":["TYPICAL_USAGE"]},
              {"role":"MECHANISM","text":"机制。","subject":null,"dimension":null,
               "aspects":["MECHANISM"]}],"caveats":[]}
            """,
            """
            {"topic":"依赖注入","statements":[
              {"role":"DEFINITION","text":"定义。","subject":null,"dimension":null,
               "aspects":["DEFINITION"],"unexpected":true},
              {"role":"MECHANISM","text":"机制。","subject":null,"dimension":null,
               "aspects":["MECHANISM"]}],"caveats":[]}
            """,
            """
            {"topic":"依赖注入","statements":[
              {"role":"DEFINITION","text":"定义。","subject":null,"dimension":null,
               "aspects":["DEFINITION"]},
              {"role":"MECHANISM","text":"机制。","subject":null,"dimension":null,
               "aspects":["MECHANISM"]}],"caveats":null}
            """
    })
    void rejectsStatesOutsideTheCanonicalExplanationSet(String payload) {
        assertThatThrownBy(() -> registry.validate(CONTRACT, payload))
                .isInstanceOf(StructuredOutputValidationException.class);
    }

    private static String validDraft() {
        return """
                {"topic":"依赖注入","statements":[
                  {"role":"DEFINITION","text":"依赖注入由外部提供对象所需依赖。",
                   "subject":null,"dimension":null,"aspects":["DEFINITION"]},
                  {"role":"MECHANISM","text":"容器解析声明并在创建对象时注入依赖。",
                   "subject":null,"dimension":null,"aspects":["MECHANISM"]}],
                 "caveats":[]}
                """;
    }
}
