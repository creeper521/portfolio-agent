package com.portfolio.agent.infrastructure.model.structured;

import com.portfolio.agent.infrastructure.model.policy.ModelOperation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeneralProviderDraftV4SchemaTest {
    private static final StructuredContractRef CONTRACT = new StructuredContractRef(
            ModelOperation.GENERAL_KNOWLEDGE, "general.provider-draft.v4");
    private final StructuredOutputContractRegistry registry =
            StructuredOutputContractRegistry.standard();

    @ParameterizedTest
    @ValueSource(strings = {
            """
            {"definition":"依赖注入是一种依赖管理方式。",
             "mechanism":"容器创建对象时提供它声明的依赖。"}
            """,
            """
            {"definition":["依赖注入是一种依赖管理方式。"],
             "mechanism":["对象声明依赖。","容器负责解析并提供依赖。"],
             "caveats":null}
            """,
            """
            {"definition":"依赖注入是一种依赖管理方式。",
             "mechanism":["对象声明依赖。"],
             "caveats":{"provider":"可以使用任意受资源限制的 JSON"},
             "topic":"不可信回显","depth":"DETAILED","unknown":42}
            """
    })
    void acceptsTheApprovedLenientWireShapes(String payload) {
        StructurallyValidatedOutput output = registry.validate(CONTRACT, payload);

        assertThat(output.contractRef()).isEqualTo(CONTRACT);
        assertThat(output.jsonTree()).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "[]",
            "null",
            "\"plain text\"",
            "{}",
            "{\"definition\":\"定义。\"}",
            "{\"mechanism\":\"机制。\"}",
            "{\"definition\":42,\"mechanism\":\"机制。\"}",
            "{\"definition\":true,\"mechanism\":\"机制。\"}",
            "{\"definition\":{},\"mechanism\":\"机制。\"}",
            "{\"definition\":[\"定义。\",42],\"mechanism\":\"机制。\"}",
            "{\"definition\":\"定义。\",\"mechanism\":[false]}"
    })
    void rejectsMissingOrInvalidCoreShapes(String payload) {
        assertThatThrownBy(() -> registry.validate(CONTRACT, payload))
                .isInstanceOf(StructuredOutputValidationException.class);
    }

    @Test
    void openCaveatsStillObeyThePreSchemaResourceGuard() {
        String payload = "{\"definition\":\"定义\",\"mechanism\":\"机制\","
                + "\"caveats\":[" + "{} ,".repeat(64) + "{}]}";

        assertThatThrownBy(() -> registry.validate(CONTRACT, payload))
                .isInstanceOf(StructuredOutputValidationException.class)
                .satisfies(failure -> {
                    StructuredOutputValidationException typed =
                            (StructuredOutputValidationException) failure;
                    assertThat(typed.getReason()).isEqualTo(
                            StructuredOutputValidationException.Reason.OUTPUT_TOO_LARGE);
                    assertThat(typed.getDiagnosticReason()).isEqualTo(
                            "OUTPUT_TOO_LARGE_RESOURCE_ARRAY_ELEMENTS");
                });
    }
}
