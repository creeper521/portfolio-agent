package com.portfolio.agent.answer.adapter.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.answer.domain.ConversationAnswerBlock;
import com.portfolio.agent.answer.domain.ConversationDraft;
import com.portfolio.agent.answer.domain.ConversationMessage;
import com.portfolio.agent.answer.domain.ConversationModelFailureCode;
import com.portfolio.agent.answer.domain.ConversationModelResult;
import com.portfolio.agent.answer.domain.ConversationRoute;
import com.portfolio.agent.answer.domain.ConversationSubjectOption;
import com.portfolio.agent.answer.domain.ConversationSuggestedQuestion;
import com.portfolio.agent.answer.domain.ConversationToolPlan;
import com.portfolio.agent.answer.domain.ConversationWindow;
import com.portfolio.agent.answer.domain.GroundingReview;
import com.portfolio.agent.answer.domain.PortfolioGroundingContext;
import com.portfolio.agent.answer.domain.PublicToolResult;
import com.portfolio.agent.answer.domain.ToolKind;
import com.portfolio.agent.answer.gateway.ConversationSummaryPort;
import com.portfolio.agent.answer.gateway.ConversationalModelPort;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class OpenAiCompatibleConversationalModelAdapter
        implements ConversationalModelPort, ConversationSummaryPort {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ConversationalPromptFactory promptFactory;
    private final ModelProviderDescriptor descriptor;
    private final String apiKey;
    private final int maxTokens;

    OpenAiCompatibleConversationalModelAdapter(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            ConversationalPromptFactory promptFactory,
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
    public ConversationModelResult<ConversationRoute> classify(
            String question,
            ConversationWindow window,
            List<ConversationSubjectOption> publicSubjects
    ) {
        Map<String, Object> conversation = conversation(question, window);
        return post(
                "intent",
                promptFactory.intentPrompt(conversation, publicSubjects),
                objectMapper.constructType(ConversationRoute.class),
                0.0);
    }

    @Override
    public ConversationModelResult<ConversationToolPlan> planTools(
            String question,
            ConversationWindow window,
            ConversationRoute route,
            PortfolioGroundingContext grounding,
            List<PublicToolResult> priorResults,
            List<ToolKind> allowedTools
    ) {
        Map<String, Object> approved = new LinkedHashMap<>();
        approved.put("route", route);
        approved.put("grounding", grounding);
        approved.put("priorResults", priorResults);
        approved.put("allowedTools", allowedTools);
        return post(
                "tool_plan",
                promptFactory.toolPlanPrompt(conversation(question, window), approved),
                objectMapper.constructType(ConversationToolPlan.class),
                0.0);
    }

    @Override
    public ConversationModelResult<ConversationDraft> generate(
            String question,
            ConversationWindow window,
            ConversationRoute route,
            PortfolioGroundingContext grounding
    ) {
        Map<String, Object> approved = new LinkedHashMap<>();
        approved.put("route", route);
        approved.put("grounding", grounding);
        return post(
                "generation",
                promptFactory.generationPrompt(conversation(question, window), approved),
                objectMapper.constructType(ConversationDraft.class),
                0.3);
    }

    @Override
    public ConversationModelResult<GroundingReview> review(
            List<ConversationAnswerBlock> blocks,
            PortfolioGroundingContext grounding
    ) {
        return post(
                "review",
                promptFactory.reviewPrompt(blocks, grounding),
                objectMapper.constructType(GroundingReview.class),
                0.0);
    }

    @Override
    public ConversationModelResult<List<ConversationSuggestedQuestion>> suggest(
            ConversationRoute route,
            ConversationWindow window,
            List<ConversationAnswerBlock> acceptedBlocks,
            List<ConversationSubjectOption> publicSubjects
    ) {
        Map<String, Object> conversation = new LinkedHashMap<>();
        conversation.put("window", window);
        conversation.put("acceptedBlocks", acceptedBlocks);
        Map<String, Object> approved = new LinkedHashMap<>();
        approved.put("route", route);
        approved.put("publicSubjects", publicSubjects);
        ConversationModelResult<SuggestedQuestionsResponse> result = post(
                "suggestion",
                promptFactory.suggestionPrompt(conversation, approved),
                objectMapper.constructType(SuggestedQuestionsResponse.class),
                0.3);
        if (!result.isSuccessful()) {
            return ConversationModelResult.failure(result.getFailureCode());
        }
        return ConversationModelResult.success(result.getValue().getQuestions());
    }

    @Override
    public Optional<String> summarize(List<ConversationMessage> messages) {
        ConversationModelResult<SummaryResponse> result = post(
                "summary",
                promptFactory.summaryPrompt(messages),
                objectMapper.constructType(SummaryResponse.class),
                0.1);
        if (!result.isSuccessful() || result.getValue().getSummary() == null
                || result.getValue().getSummary().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(result.getValue().getSummary());
    }

    private Map<String, Object> conversation(String question, ConversationWindow window) {
        Map<String, Object> conversation = new LinkedHashMap<>();
        conversation.put("question", question);
        conversation.put("summary", window.getSummary().orElse(null));
        conversation.put("messages", window.getRecentMessages());
        return conversation;
    }

    private <T> ConversationModelResult<T> post(
            String operation,
            String userPrompt,
            JavaType responseType,
            double temperature
    ) {
        ChatCompletionRequest request = new ChatCompletionRequest(
                descriptor.getModelName(),
                List.of(
                        new ChatMessage("system", promptFactory.systemPrompt(operation)),
                        new ChatMessage("user", userPrompt)),
                new ResponseFormat("json_object"),
                new Thinking("disabled"),
                false,
                maxTokens,
                temperature);
        try {
            ChatCompletionResponse response = restClient.post()
                    .uri(descriptor.getEndpoint())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(request)
                    .retrieve()
                    .body(ChatCompletionResponse.class);
            String content = responseContent(response);
            if (content == null || content.isBlank()) {
                return ConversationModelResult.failure(
                        ConversationModelFailureCode.EMPTY_RESPONSE);
            }
            T value = objectMapper.readerFor(responseType)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(content);
            return ConversationModelResult.success(value);
        } catch (RestClientException exception) {
            return ConversationModelResult.failure(isTimeout(exception)
                    ? ConversationModelFailureCode.TIMEOUT
                    : ConversationModelFailureCode.PROVIDER_ERROR);
        } catch (Exception exception) {
            return ConversationModelResult.failure(
                    ConversationModelFailureCode.INVALID_RESPONSE);
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

        private ResponseFormat(String type) {
            this.type = type;
        }

        public String getType() { return type; }
    }

    private static final class Thinking {
        private final String type;

        private Thinking(String type) {
            this.type = type;
        }

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

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class SummaryResponse {
        private String summary;

        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    private static final class SuggestedQuestionsResponse {
        private List<ConversationSuggestedQuestion> questions;

        public List<ConversationSuggestedQuestion> getQuestions() {
            return questions == null ? List.of() : List.copyOf(questions);
        }

        public void setQuestions(List<ConversationSuggestedQuestion> questions) {
            this.questions = questions;
        }
    }
}
