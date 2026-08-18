package com.portfolio.agent.turn.infrastructure.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.answer.adapter.model.ModelProviderDescriptor;
import com.portfolio.agent.common.observability.DiagnosticEvent;
import com.portfolio.agent.common.observability.DiagnosticEventPublisher;
import com.portfolio.agent.common.observability.DiagnosticLevel;
import com.portfolio.agent.turn.planning.GoalInterpretationInput;
import com.portfolio.agent.turn.planning.GoalInterpretationPort;
import com.portfolio.agent.turn.planning.GoalInterpretationResult;
import com.portfolio.agent.turn.planning.GoalProposalCodec;
import com.portfolio.agent.turn.planning.GoalInterpretationUnavailableException;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class GoalInterpretationAdapter implements GoalInterpretationPort {

    private static final String SYSTEM_PROMPT = """
            Interpret the visitor input into the closed user-goal JSON schema supplied in the data.
            Emit JSON only. You may describe user goals or a clarification need, but you have no
            authority to design execution plans, select tools, choose providers, or invent subjects.
            """;

    private final RestClient restClient;
    private final ObjectMapper mapper;
    private final GoalProposalCodec codec;
    private final ModelProviderDescriptor descriptor;
    private final String apiKey;
    private final int maxOutputTokens;
    private final DiagnosticEventPublisher diagnostics;

    public GoalInterpretationAdapter(
            RestClient.Builder builder,
            ObjectMapper mapper,
            GoalProposalCodec codec,
            ModelProviderDescriptor descriptor,
            String apiKey,
            int maxOutputTokens,
            DiagnosticEventPublisher diagnostics) {
        this.restClient = Objects.requireNonNull(builder, "builder").build();
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.apiKey = apiKey == null ? "" : apiKey;
        if (maxOutputTokens < 1 || maxOutputTokens > 4000) {
            throw new IllegalArgumentException("maxOutputTokens is invalid");
        }
        this.maxOutputTokens = maxOutputTokens;
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
    }

    @Override
    public GoalInterpretationResult interpret(GoalInterpretationInput input) {
        Objects.requireNonNull(input, "input");
        long startedAt = System.nanoTime();
        try {
            ChatCompletionResponse response = restClient.post()
                    .uri(descriptor.getEndpoint())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(new ChatCompletionRequest(
                            descriptor.getModelName(),
                            List.of(new ChatMessage("system", SYSTEM_PROMPT),
                                    new ChatMessage("user", prompt(input))),
                            new ResponseFormat("json_object"), false,
                            maxOutputTokens, 0.0))
                    .retrieve()
                    .body(ChatCompletionResponse.class);
            String content = responseContent(response);
            if (content == null || content.isBlank()) {
                throw new GoalInterpretationUnavailableException();
            }
            GoalInterpretationResult result = codec.decode(content, input);
            publish("model.goal-interpretation.completed", DiagnosticLevel.DEBUG, startedAt);
            return result;
        } catch (RestClientException | IllegalArgumentException failure) {
            publish("model.goal-interpretation.failed", DiagnosticLevel.WARN, startedAt);
            throw new GoalInterpretationUnavailableException(failure);
        }
    }

    private String prompt(GoalInterpretationInput input) {
        Map<String, Object> projection = new LinkedHashMap<>();
        projection.put("currentInput", input.getUserText());
        projection.put("recentConversation", input.getRecentMessages());
        projection.put("allowedGoalKinds", input.getAllowedGoalKinds());
        projection.put("publicSubjects", input.getPublicSubjects().stream().map(subject -> {
            Map<String, Object> descriptorMap = new LinkedHashMap<>();
            descriptorMap.put("kind", subject.getKind());
            descriptorMap.put("reference", subject.getReference());
            descriptorMap.put("reviewedLabel", subject.getLabel());
            descriptorMap.put("reviewedAliases", subject.getReviewedAliases());
            return descriptorMap;
        }).toList());
        projection.put("schema", "user-goal-proposal-v1");
        try {
            return mapper.writeValueAsString(projection);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("unable to project goal interpretation input", failure);
        }
    }

    private String responseContent(ChatCompletionResponse response) {
        if (response == null || response.getChoices() == null
                || response.getChoices().size() != 1
                || response.getChoices().getFirst().getMessage() == null) {
            return null;
        }
        return response.getChoices().getFirst().getMessage().getContent();
    }

    private void publish(String eventName, DiagnosticLevel level, long startedAt) {
        try {
            diagnostics.publish(DiagnosticEvent.builder(eventName, level)
                    .field("provider.operation", "GOAL_INTERPRETATION")
                    .field("provider.id", descriptor.getProviderId().name())
                    .field("duration.bucket", durationBucket(startedAt))
                    .build());
        } catch (RuntimeException ignored) {
            // Diagnostics never change interpretation behavior.
        }
    }

    private String durationBucket(long startedAt) {
        long millis = (System.nanoTime() - startedAt) / 1_000_000L;
        if (millis < 100) return "LT_100_MS";
        if (millis < 500) return "LT_500_MS";
        if (millis < 2000) return "LT_2_S";
        return "GTE_2_S";
    }

    private static final class ChatCompletionRequest {
        private final String model;
        private final List<ChatMessage> messages;
        private final ResponseFormat responseFormat;
        private final boolean stream;
        private final int maxTokens;
        private final double temperature;

        private ChatCompletionRequest(
                String model,
                List<ChatMessage> messages,
                ResponseFormat responseFormat,
                boolean stream,
                int maxTokens,
                double temperature) {
            this.model = model;
            this.messages = List.copyOf(messages);
            this.responseFormat = responseFormat;
            this.stream = stream;
            this.maxTokens = maxTokens;
            this.temperature = temperature;
        }

        public String getModel() { return model; }
        public List<ChatMessage> getMessages() { return messages; }
        @JsonProperty("response_format") public ResponseFormat getResponseFormat() { return responseFormat; }
        public boolean isStream() { return stream; }
        @JsonProperty("max_tokens") public int getMaxTokens() { return maxTokens; }
        public double getTemperature() { return temperature; }
    }

    private static final class ChatMessage {
        private final String role;
        private final String content;
        private ChatMessage(String role, String content) { this.role = role; this.content = content; }
        public String getRole() { return role; }
        public String getContent() { return content; }
    }

    private static final class ResponseFormat {
        private final String type;
        private ResponseFormat(String type) { this.type = type; }
        public String getType() { return type; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class ChatCompletionResponse {
        private List<Choice> choices;
        public List<Choice> getChoices() { return choices; }
        public void setChoices(List<Choice> choices) { this.choices = choices; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class Choice {
        private ResponseMessage message;
        public ResponseMessage getMessage() { return message; }
        public void setMessage(ResponseMessage message) { this.message = message; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class ResponseMessage {
        private String content;
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }
}
