package com.portfolio.agent.answer.adapter.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.answer.domain.ModelAnswerDraft;
import com.portfolio.agent.answer.domain.ModelExpressionFailureCode;
import com.portfolio.agent.answer.domain.ModelExpressionRequest;
import com.portfolio.agent.answer.domain.ModelExpressionResult;
import com.portfolio.agent.answer.gateway.ModelExpressionPort;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.List;

public final class OpenAiCompatibleModelExpressionAdapter implements ModelExpressionPort {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ModelPromptFactory promptFactory;
    private final ModelProviderDescriptor descriptor;
    private final String apiKey;
    private final int maxTokens;

    OpenAiCompatibleModelExpressionAdapter(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            ModelPromptFactory promptFactory,
            ModelProviderDescriptor descriptor,
            String apiKey,
            int maxTokens
    ) {
        this.restClient = builder.build();
        this.objectMapper = objectMapper.copy()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.promptFactory = promptFactory;
        this.descriptor = descriptor;
        this.apiKey = apiKey;
        this.maxTokens = maxTokens;
    }

    @Override
    public ModelExpressionResult express(ModelExpressionRequest request) {
        ChatCompletionRequest providerRequest;
        try {
            providerRequest = requestBody(request);
        } catch (RuntimeException exception) {
            return ModelExpressionResult.failure(
                    ModelExpressionFailureCode.REQUEST_BUILD_FAILED);
        }
        try {
            ChatCompletionResponse response = restClient.post()
                    .uri(descriptor.getEndpoint())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(providerRequest)
                    .retrieve()
                    .body(ChatCompletionResponse.class);
            String content = responseContent(response);
            if (content == null || content.isBlank()) {
                return ModelExpressionResult.failure(
                        ModelExpressionFailureCode.EMPTY_RESPONSE);
            }
            ModelAnswerDraft draft = objectMapper.readerFor(ModelAnswerDraft.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(content);
            return ModelExpressionResult.success(draft);
        } catch (RestClientException exception) {
            return ModelExpressionResult.failure(
                    isTimeout(exception)
                            ? ModelExpressionFailureCode.TIMEOUT
                            : ModelExpressionFailureCode.PROVIDER_ERROR);
        } catch (Exception exception) {
            return ModelExpressionResult.failure(
                    ModelExpressionFailureCode.INVALID_RESPONSE);
        }
    }

    private boolean isTimeout(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof HttpTimeoutException
                    || current instanceof SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private ChatCompletionRequest requestBody(ModelExpressionRequest request) {
        return new ChatCompletionRequest(
                descriptor.getModelName(),
                List.of(
                        new ChatMessage("system", promptFactory.systemPrompt(
                                request.getAnswerSchemaVersion())),
                        new ChatMessage("user", promptFactory.planPrompt(request))
                ),
                new ResponseFormat("json_object"),
                new Thinking("disabled"),
                false,
                maxTokens,
                0.2
        );
    }

    private String responseContent(ChatCompletionResponse response) {
        if (response == null || response.getChoices() == null
                || response.getChoices().size() != 1
                || response.getChoices().getFirst().getMessage() == null) {
            return null;
        }
        return response.getChoices().getFirst().getMessage().getContent();
    }

    private static final class ChatCompletionRequest {
        private final String model;
        private final List<ChatMessage> messages;
        private final ResponseFormat responseFormat;
        private final Thinking thinking;
        private final boolean stream;
        private final int maxTokens;
        private final double temperature;

        private ChatCompletionRequest(
                String model,
                List<ChatMessage> messages,
                ResponseFormat responseFormat,
                Thinking thinking,
                boolean stream,
                int maxTokens,
                double temperature
        ) {
            this.model = model;
            this.messages = messages;
            this.responseFormat = responseFormat;
            this.thinking = thinking;
            this.stream = stream;
            this.maxTokens = maxTokens;
            this.temperature = temperature;
        }

        public String getModel() { return model; }
        public List<ChatMessage> getMessages() { return messages; }
        @JsonProperty("response_format")
        public ResponseFormat getResponseFormat() { return responseFormat; }
        public Thinking getThinking() { return thinking; }
        public boolean isStream() { return stream; }
        @JsonProperty("max_tokens")
        public int getMaxTokens() { return maxTokens; }
        public double getTemperature() { return temperature; }
    }

    private static final class ChatMessage {
        private final String role;
        private final String content;

        private ChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() { return role; }
        public String getContent() { return content; }
    }

    private static final class ResponseFormat {
        private final String type;

        private ResponseFormat(String type) { this.type = type; }
        public String getType() { return type; }
    }

    private static final class Thinking {
        private final String type;

        private Thinking(String type) { this.type = type; }
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
