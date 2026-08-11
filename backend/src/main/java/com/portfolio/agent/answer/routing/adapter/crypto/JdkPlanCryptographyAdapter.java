package com.portfolio.agent.answer.routing.adapter.crypto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.portfolio.agent.answer.routing.domain.PlanConfirmation;
import com.portfolio.agent.answer.routing.domain.PlanExclusion;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes;
import com.portfolio.agent.answer.routing.domain.SemanticTask;
import com.portfolio.agent.answer.routing.domain.SemanticTaskParameters;
import com.portfolio.agent.answer.routing.domain.SemanticTurnPlan;
import com.portfolio.agent.answer.routing.domain.SubjectReference;
import com.portfolio.agent.answer.routing.domain.TaskConfidence;
import com.portfolio.agent.answer.routing.domain.TaskDependency;
import com.portfolio.agent.answer.routing.service.ValidatedSemanticTurnPlan;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** JDK-only AES-GCM envelope and detached HMAC binding implementation. */
public final class JdkPlanCryptographyAdapter implements PlanCryptographyPort {

    private static final String ENVELOPE_VERSION = "stp-confirmation-v1";
    private static final int AES_KEY_BYTES = 32;
    private static final int HMAC_KEY_MINIMUM_BYTES = 32;
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final SecretKey encryptionKey;
    private final SecretKey integrityKey;
    private final SecureRandom secureRandom;
    private final ObjectMapper objectMapper;

    public JdkPlanCryptographyAdapter(byte[] encryptionKey, byte[] integrityKey) {
        this(encryptionKey, integrityKey, new SecureRandom());
    }

    public JdkPlanCryptographyAdapter(byte[] encryptionKey, byte[] integrityKey, SecureRandom secureRandom) {
        this.encryptionKey = aesKey(encryptionKey);
        this.integrityKey = hmacKey(integrityKey);
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
        this.objectMapper = new ObjectMapper();
    }

    public static JdkPlanCryptographyAdapter fromBase64(String encryptionKey, String integrityKey) {
        try {
            return new JdkPlanCryptographyAdapter(
                    Base64.getDecoder().decode(requireText(encryptionKey, "encryptionKey")),
                    Base64.getDecoder().decode(requireText(integrityKey, "integrityKey")));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("plan confirmation key configuration is invalid");
        }
    }

    @Override
    public SealedPlan seal(
            ValidatedSemanticTurnPlan plan,
            PlanConfirmation.Identity identity,
            PlanConfirmation.VersionBinding versionBinding) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(versionBinding, "versionBinding");
        if (!plan.getPlanFingerprint().equals(identity.getPlanFingerprint())) {
            throw new IllegalArgumentException("plan fingerprint must match confirmation identity");
        }

        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            secureRandom.nextBytes(iv);
            byte[] plainText = objectMapper.writeValueAsBytes(encodePayload(plan.getPlan(), identity, versionBinding));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(associatedData(identity).getBytes(StandardCharsets.UTF_8));
            byte[] cipherText = cipher.doFinal(plainText);
            byte[] envelopeBytes = ByteBuffer.allocate(1 + iv.length + cipherText.length)
                    .put((byte) 1)
                    .put(iv)
                    .put(cipherText)
                    .array();
            String confirmationPlan = Base64.getUrlEncoder().withoutPadding().encodeToString(envelopeBytes);
            String integrityToken = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    hmac(tokenBinding(identity.getConfirmationId(), identity.getPlanFingerprint(), confirmationPlan)));
            return new SealedPlan(confirmationPlan, integrityToken);
        } catch (GeneralSecurityException | JsonProcessingException exception) {
            throw new IllegalStateException("plan confirmation sealing is unavailable");
        }
    }

    @Override
    public boolean isIntegrityValid(PlanConfirmation.Submission submission) {
        Objects.requireNonNull(submission, "submission");
        try {
            byte[] expected = hmac(tokenBinding(
                    submission.getConfirmationId(),
                    submission.getPlanFingerprint(),
                    submission.getConfirmationPlan()));
            byte[] actual = Base64.getUrlDecoder().decode(submission.getIntegrityToken());
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException | GeneralSecurityException exception) {
            return false;
        }
    }

    @Override
    public OpenedPlan open(PlanConfirmation.Submission submission) {
        Objects.requireNonNull(submission, "submission");
        if (!isIntegrityValid(submission)) {
            throw new IllegalArgumentException("plan confirmation is invalid");
        }
        try {
            byte[] envelope = Base64.getUrlDecoder().decode(submission.getConfirmationPlan());
            if (envelope.length <= 1 + GCM_IV_BYTES || envelope[0] != 1) {
                throw new IllegalArgumentException("plan confirmation is invalid");
            }
            ByteBuffer buffer = ByteBuffer.wrap(envelope);
            buffer.get();
            byte[] iv = new byte[GCM_IV_BYTES];
            buffer.get(iv);
            byte[] cipherText = new byte[buffer.remaining()];
            buffer.get(cipherText);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(associatedData(submission.getConfirmationId(), submission.getPlanFingerprint())
                    .getBytes(StandardCharsets.UTF_8));
            JsonNode payload = objectMapper.readTree(cipher.doFinal(cipherText));
            PlanConfirmation.Identity identity = decodeIdentity(requiredObject(payload, "identity"));
            if (!identity.getConfirmationId().equals(submission.getConfirmationId())
                    || !identity.getPlanFingerprint().equals(submission.getPlanFingerprint())) {
                throw new IllegalArgumentException("plan confirmation is invalid");
            }
            return new OpenedPlan(
                    decodePlan(requiredObject(payload, "plan")),
                    identity,
                    decodeVersionBinding(requiredObject(payload, "versionBinding")));
        } catch (GeneralSecurityException | IOException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("plan confirmation is invalid");
        }
    }

    private ObjectNode encodePayload(
            SemanticTurnPlan plan,
            PlanConfirmation.Identity identity,
            PlanConfirmation.VersionBinding versionBinding) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.set("identity", encodeIdentity(identity));
        payload.set("versionBinding", encodeVersionBinding(versionBinding));
        payload.set("plan", encodePlan(plan));
        return payload;
    }

    private ObjectNode encodeIdentity(PlanConfirmation.Identity identity) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("confirmationId", identity.getConfirmationId());
        node.put("issuedAt", identity.getIssuedAt().toString());
        node.put("expiresAt", identity.getExpiresAt().toString());
        node.put("planFingerprint", identity.getPlanFingerprint());
        return node;
    }

    private ObjectNode encodeVersionBinding(PlanConfirmation.VersionBinding versionBinding) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("schemaVersion", versionBinding.getSchemaVersion());
        node.put("contentVersion", versionBinding.getContentVersion());
        node.put("subjectVersion", versionBinding.getSubjectVersion());
        node.put("capabilitySetVersion", versionBinding.getCapabilitySetVersion());
        return node;
    }

    private ObjectNode encodePlan(SemanticTurnPlan plan) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("planId", plan.getPlanId());
        node.put("contentVersion", plan.getContentVersion());
        node.put("source", plan.getSource().name());
        if (plan.getPlanFingerprint() != null) {
            node.put("planFingerprint", plan.getPlanFingerprint());
        }
        ArrayNode tasks = node.putArray("tasks");
        for (SemanticTask task : plan.getTasks()) {
            tasks.add(encodeTask(task));
        }
        ArrayNode dependencies = node.putArray("dependencies");
        for (TaskDependency dependency : plan.getDependencies()) {
            ObjectNode item = dependencies.addObject();
            item.put("fromTaskId", dependency.getFromTaskId());
            item.put("toTaskId", dependency.getToTaskId());
            item.put("type", dependency.getType().name());
            item.put("origin", dependency.getOrigin().name());
        }
        ArrayNode exclusions = node.putArray("exclusions");
        for (PlanExclusion exclusion : plan.getExclusions()) {
            exclusions.add(encodeExclusion(exclusion));
        }
        node.set("requestedOutputs", encodeEnums(plan.getRequestedOutputs()));
        ObjectNode policy = node.putObject("confirmationPolicy");
        policy.put("confirmationRequired", plan.getConfirmationPolicy().isConfirmationRequired());
        policy.set("triggerCodes", encodeEnums(plan.getConfirmationPolicy().getTriggerCodes()));
        return node;
    }

    private ObjectNode encodeTask(SemanticTask task) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("taskId", task.getTaskId());
        node.put("taskType", task.getTaskType().name());
        node.put("sourceDomain", task.getSourceDomain().name());
        node.put("goalLabel", task.getGoalLabel());
        node.set("parameters", encodeParameters(task.getParameters()));
        node.set("requestedOutputs", encodeEnums(task.getRequestedOutputs()));
        ObjectNode confidence = node.putObject("confidence");
        confidence.put("overall", task.getConfidence().getOverall().name());
        confidence.put("origin", task.getConfidence().getOrigin().name());
        ObjectNode fields = confidence.putObject("fieldLevels");
        for (Map.Entry<SemanticRoutingTypes.ConfidenceField, SemanticRoutingTypes.ConfidenceLevel> entry
                : task.getConfidence().getFieldLevels().entrySet()) {
            fields.put(entry.getKey().name(), entry.getValue().name());
        }
        ArrayNode subjects = node.putArray("subjectReferences");
        for (SubjectReference subject : task.getSubjectReferences()) {
            subjects.add(encodeSubject(subject));
        }
        return node;
    }

    private ObjectNode encodeParameters(SemanticTaskParameters parameters) {
        ObjectNode node = objectMapper.createObjectNode();
        if (parameters instanceof SemanticTaskParameters.PortfolioFact fact) {
            node.put("kind", "PORTFOLIO_FACT");
            node.set("subject", encodeSubject(fact.getSubject()));
            node.set("facets", encodeEnums(fact.getFacets()));
            node.put("audienceRole", fact.getAudienceRole().name());
            return node;
        }
        if (parameters instanceof SemanticTaskParameters.PortfolioCompare comparison) {
            node.put("kind", "PORTFOLIO_COMPARE");
            node.set("subjects", encodeSubjects(comparison.getSubjects()));
            node.set("dimensions", encodeEnums(comparison.getDimensions()));
            node.put("audienceRole", comparison.getAudienceRole().name());
            return node;
        }
        if (parameters instanceof SemanticTaskParameters.PortfolioRecommend recommendation) {
            node.put("kind", "PORTFOLIO_RECOMMEND");
            node.set("candidateSubjects", encodeSubjects(recommendation.getCandidateSubjects()));
            node.put("careerTrack", recommendation.getCareerTrack().name());
            node.set("capabilityCodes", encodeEnums(recommendation.getCapabilityCodes()));
            node.put("goal", recommendation.getGoal());
            node.put("requestedSize", recommendation.getRequestedSize().getValue());
            node.put("audienceRole", recommendation.getAudienceRole().name());
            return node;
        }
        if (parameters instanceof SemanticTaskParameters.PortfolioRefinement refinement) {
            node.put("kind", "PORTFOLIO_REFINEMENT");
            node.set("baseResultReference", encodeSubject(refinement.getBaseResultReference()));
            node.set("addedConstraints", encodeEnums(refinement.getAddedConstraints()));
            node.set("removedSubjects", encodeSubjects(refinement.getRemovedSubjects()));
            return node;
        }
        if (parameters instanceof SemanticTaskParameters.GeneralExplanation explanation) {
            node.put("kind", "GENERAL_EXPLANATION");
            node.put("topic", explanation.getTopic());
            node.put("depth", explanation.getDepth().name());
            node.put("audienceRole", explanation.getAudienceRole().name());
            return node;
        }
        if (parameters instanceof SemanticTaskParameters.GeneralComparison comparison) {
            node.put("kind", "GENERAL_COMPARISON");
            node.set("subjects", encodeStrings(comparison.getSubjects()));
            node.set("dimensions", encodeEnums(comparison.getDimensions()));
            node.put("depth", comparison.getDepth().name());
            node.put("audienceRole", comparison.getAudienceRole().name());
            return node;
        }
        if (parameters instanceof SemanticTaskParameters.Synthesis synthesis) {
            node.put("kind", "SYNTHESIS");
            node.set("sourceTaskIds", encodeStrings(synthesis.getSourceTaskIds()));
            node.put("synthesisGoal", synthesis.getSynthesisGoal());
            node.set("dimensions", encodeEnums(synthesis.getDimensions()));
            return node;
        }
        throw new IllegalArgumentException("unsupported semantic task parameters");
    }

    private ObjectNode encodeExclusion(PlanExclusion exclusion) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("scope", exclusion.getScope().name());
        node.put("type", exclusion.getType().name());
        if (exclusion.getTaskId() != null) {
            node.put("taskId", exclusion.getTaskId());
        }
        if (exclusion.getControlledValue() instanceof PlanExclusion.SubjectValue value) {
            node.put("valueKind", "SUBJECT");
            node.set("subject", encodeSubject(value.getSubject()));
        } else if (exclusion.getControlledValue() instanceof PlanExclusion.OutputValue value) {
            node.put("valueKind", "OUTPUT");
            node.put("output", value.getOutput().name());
        } else if (exclusion.getControlledValue() instanceof PlanExclusion.DimensionValue value) {
            node.put("valueKind", "DIMENSION");
            node.put("dimension", value.getDimension().name());
        } else if (exclusion.getControlledValue() instanceof PlanExclusion.ConstraintValue value) {
            node.put("valueKind", "CONSTRAINT");
            node.put("constraint", value.getConstraint().name());
        } else {
            throw new IllegalArgumentException("unsupported plan exclusion");
        }
        return node;
    }

    private ObjectNode encodeSubject(SubjectReference subject) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("subjectType", subject.getSubjectType().name());
        node.put("subjectId", subject.getSubjectId());
        node.put("resolutionSource", subject.getResolutionSource().name());
        if (subject.getContentVersion() != null) {
            node.put("contentVersion", subject.getContentVersion());
        }
        return node;
    }

    private ArrayNode encodeSubjects(Iterable<SubjectReference> subjects) {
        ArrayNode node = objectMapper.createArrayNode();
        for (SubjectReference subject : subjects) {
            node.add(encodeSubject(subject));
        }
        return node;
    }

    private ArrayNode encodeEnums(Iterable<? extends Enum<?>> values) {
        ArrayNode node = objectMapper.createArrayNode();
        for (Enum<?> value : values) {
            node.add(value.name());
        }
        return node;
    }

    private ArrayNode encodeStrings(Iterable<String> values) {
        ArrayNode node = objectMapper.createArrayNode();
        for (String value : values) {
            node.add(value);
        }
        return node;
    }

    private PlanConfirmation.Identity decodeIdentity(JsonNode node) {
        return new PlanConfirmation.Identity(
                requiredText(node, "confirmationId"),
                Instant.parse(requiredText(node, "issuedAt")),
                Instant.parse(requiredText(node, "expiresAt")),
                requiredText(node, "planFingerprint"));
    }

    private PlanConfirmation.VersionBinding decodeVersionBinding(JsonNode node) {
        return new PlanConfirmation.VersionBinding(
                requiredText(node, "schemaVersion"),
                requiredText(node, "contentVersion"),
                requiredText(node, "subjectVersion"),
                requiredText(node, "capabilitySetVersion"));
    }

    private SemanticTurnPlan decodePlan(JsonNode node) {
        List<SemanticTask> tasks = new ArrayList<>();
        for (JsonNode task : requiredArray(node, "tasks")) {
            tasks.add(decodeTask(task));
        }
        List<TaskDependency> dependencies = new ArrayList<>();
        for (JsonNode dependency : requiredArray(node, "dependencies")) {
            dependencies.add(new TaskDependency(
                    requiredText(dependency, "fromTaskId"),
                    requiredText(dependency, "toTaskId"),
                    enumValue(SemanticRoutingTypes.TaskDependencyType.class, dependency, "type"),
                    enumValue(SemanticRoutingTypes.DependencyOrigin.class, dependency, "origin")));
        }
        List<PlanExclusion> exclusions = new ArrayList<>();
        for (JsonNode exclusion : requiredArray(node, "exclusions")) {
            exclusions.add(decodeExclusion(exclusion));
        }
        JsonNode policy = requiredObject(node, "confirmationPolicy");
        return new SemanticTurnPlan(
                requiredText(node, "planId"),
                requiredText(node, "contentVersion"),
                enumValue(SemanticTurnPlan.PlanSource.class, node, "source"),
                tasks,
                dependencies,
                exclusions,
                enumSet(SemanticRoutingTypes.RequestedOutput.class, requiredArray(node, "requestedOutputs")),
                new SemanticTurnPlan.PlanConfirmationPolicy(
                        requiredBoolean(policy, "confirmationRequired"),
                        enumSet(SemanticTurnPlan.ConfirmationTrigger.class, requiredArray(policy, "triggerCodes"))),
                optionalText(node, "planFingerprint"));
    }

    private SemanticTask decodeTask(JsonNode node) {
        List<SubjectReference> subjects = decodeSubjectList(requiredArray(node, "subjectReferences"));
        JsonNode confidence = requiredObject(node, "confidence");
        ObjectNode fieldLevels = (ObjectNode) requiredObject(confidence, "fieldLevels");
        java.util.EnumMap<SemanticRoutingTypes.ConfidenceField, SemanticRoutingTypes.ConfidenceLevel> levels =
                new java.util.EnumMap<>(SemanticRoutingTypes.ConfidenceField.class);
        java.util.Iterator<Map.Entry<String, JsonNode>> entries = fieldLevels.fields();
        while (entries.hasNext()) {
            Map.Entry<String, JsonNode> entry = entries.next();
            levels.put(
                    SemanticRoutingTypes.ConfidenceField.valueOf(entry.getKey()),
                    SemanticRoutingTypes.ConfidenceLevel.valueOf(entry.getValue().asText()));
        }
        return SemanticTask.create(
                requiredText(node, "taskId"),
                enumValue(SemanticRoutingTypes.SemanticTaskType.class, node, "taskType"),
                enumValue(SemanticRoutingTypes.TaskSourceDomain.class, node, "sourceDomain"),
                requiredText(node, "goalLabel"),
                decodeParameters(requiredObject(node, "parameters")),
                enumSet(SemanticRoutingTypes.RequestedOutput.class, requiredArray(node, "requestedOutputs")),
                new TaskConfidence(
                        enumValue(SemanticRoutingTypes.ConfidenceLevel.class, confidence, "overall"),
                        levels,
                        enumValue(SemanticRoutingTypes.ConfidenceOrigin.class, confidence, "origin")),
                subjects);
    }

    private SemanticTaskParameters decodeParameters(JsonNode node) {
        String kind = requiredText(node, "kind");
        return switch (kind) {
            case "PORTFOLIO_FACT" -> new SemanticTaskParameters.PortfolioFact(
                    decodeSubject(requiredObject(node, "subject")),
                    stringSet(requiredArray(node, "facets")),
                    requiredText(node, "audienceRole"));
            case "PORTFOLIO_COMPARE" -> new SemanticTaskParameters.PortfolioCompare(
                    decodeSubjectList(requiredArray(node, "subjects")),
                    stringSet(requiredArray(node, "dimensions")),
                    requiredText(node, "audienceRole"));
            case "PORTFOLIO_RECOMMEND" -> new SemanticTaskParameters.PortfolioRecommend(
                    decodeSubjectList(requiredArray(node, "candidateSubjects")),
                    requiredText(node, "careerTrack"),
                    stringSet(requiredArray(node, "capabilityCodes")),
                    requiredText(node, "goal"),
                    requiredInt(node, "requestedSize"),
                    requiredText(node, "audienceRole"));
            case "PORTFOLIO_REFINEMENT" -> new SemanticTaskParameters.PortfolioRefinement(
                    decodeSubject(requiredObject(node, "baseResultReference")),
                    stringSet(requiredArray(node, "addedConstraints")),
                    new LinkedHashSet<>(decodeSubjectList(requiredArray(node, "removedSubjects"))));
            case "GENERAL_EXPLANATION" -> new SemanticTaskParameters.GeneralExplanation(
                    requiredText(node, "topic"),
                    requiredText(node, "depth"),
                    requiredText(node, "audienceRole"));
            case "GENERAL_COMPARISON" -> new SemanticTaskParameters.GeneralComparison(
                    stringList(requiredArray(node, "subjects")),
                    stringSet(requiredArray(node, "dimensions")),
                    requiredText(node, "depth"),
                    requiredText(node, "audienceRole"));
            case "SYNTHESIS" -> new SemanticTaskParameters.Synthesis(
                    stringList(requiredArray(node, "sourceTaskIds")),
                    requiredText(node, "synthesisGoal"),
                    stringSet(requiredArray(node, "dimensions")));
            default -> throw new IllegalArgumentException("unsupported semantic task parameters");
        };
    }

    private PlanExclusion decodeExclusion(JsonNode node) {
        SemanticRoutingTypes.ExclusionScope scope = enumValue(
                SemanticRoutingTypes.ExclusionScope.class, node, "scope");
        SemanticRoutingTypes.ExclusionType type = enumValue(
                SemanticRoutingTypes.ExclusionType.class, node, "type");
        String taskId = optionalText(node, "taskId");
        String kind = requiredText(node, "valueKind");
        PlanExclusion.ExclusionValue value = switch (kind) {
            case "SUBJECT" -> new PlanExclusion.SubjectValue(decodeSubject(requiredObject(node, "subject")));
            case "OUTPUT" -> new PlanExclusion.OutputValue(enumValue(
                    SemanticRoutingTypes.RequestedOutput.class, node, "output"));
            case "DIMENSION" -> new PlanExclusion.DimensionValue(enumValue(
                    SemanticRoutingTypes.ComparisonDimension.class, node, "dimension"));
            case "CONSTRAINT" -> new PlanExclusion.ConstraintValue(enumValue(
                    SemanticRoutingTypes.ConstraintCode.class, node, "constraint"));
            default -> throw new IllegalArgumentException("unsupported plan exclusion");
        };
        return new PlanExclusion(scope, type, taskId, value);
    }

    private SubjectReference decodeSubject(JsonNode node) {
        return new SubjectReference(
                enumValue(SemanticRoutingTypes.SubjectType.class, node, "subjectType"),
                requiredText(node, "subjectId"),
                enumValue(SemanticRoutingTypes.SubjectResolutionSource.class, node, "resolutionSource"),
                optionalText(node, "contentVersion"));
    }

    private List<SubjectReference> decodeSubjectList(JsonNode node) {
        List<SubjectReference> subjects = new ArrayList<>();
        for (JsonNode subject : node) {
            subjects.add(decodeSubject(subject));
        }
        return List.copyOf(subjects);
    }

    private static List<String> stringList(JsonNode node) {
        List<String> values = new ArrayList<>();
        for (JsonNode value : node) {
            if (!value.isTextual()) {
                throw new IllegalArgumentException("plan confirmation is invalid");
            }
            values.add(value.asText());
        }
        return List.copyOf(values);
    }

    private static Set<String> stringSet(JsonNode node) {
        return Set.copyOf(stringList(node));
    }

    private static <E extends Enum<E>> Set<E> enumSet(Class<E> type, JsonNode node) {
        Set<E> values = new LinkedHashSet<>();
        for (JsonNode value : node) {
            if (!value.isTextual()) {
                throw new IllegalArgumentException("plan confirmation is invalid");
            }
            values.add(Enum.valueOf(type, value.asText()));
        }
        return Set.copyOf(values);
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, JsonNode node, String field) {
        return Enum.valueOf(type, requiredText(node, field));
    }

    private static ObjectNode requiredObject(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (!(value instanceof ObjectNode object)) {
            throw new IllegalArgumentException("plan confirmation is invalid");
        }
        return object;
    }

    private static ArrayNode requiredArray(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (!(value instanceof ArrayNode array)) {
            throw new IllegalArgumentException("plan confirmation is invalid");
        }
        return array;
    }

    private static boolean requiredBoolean(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isBoolean()) {
            throw new IllegalArgumentException("plan confirmation is invalid");
        }
        return value.booleanValue();
    }

    private static int requiredInt(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.canConvertToInt()) {
            throw new IllegalArgumentException("plan confirmation is invalid");
        }
        return value.intValue();
    }

    private static String requiredText(JsonNode node, String field) {
        String value = optionalText(node, field);
        if (value == null) {
            throw new IllegalArgumentException("plan confirmation is invalid");
        }
        return value;
    }

    private static String optionalText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("plan confirmation is invalid");
        }
        return value.asText().trim();
    }

    private byte[] hmac(byte[] binding) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(integrityKey);
        return mac.doFinal(binding);
    }

    private static byte[] tokenBinding(String confirmationId, String planFingerprint, String confirmationPlan) {
        return (ENVELOPE_VERSION + "\n" + confirmationId + "\n" + planFingerprint + "\n" + confirmationPlan)
                .getBytes(StandardCharsets.UTF_8);
    }

    private static String associatedData(PlanConfirmation.Identity identity) {
        return associatedData(identity.getConfirmationId(), identity.getPlanFingerprint());
    }

    private static String associatedData(String confirmationId, String planFingerprint) {
        return ENVELOPE_VERSION + "\n" + confirmationId + "\n" + planFingerprint;
    }

    private static SecretKey aesKey(byte[] value) {
        if (value == null || value.length != AES_KEY_BYTES) {
            throw new IllegalArgumentException("plan confirmation key configuration is invalid");
        }
        return new SecretKeySpec(value.clone(), "AES");
    }

    private static SecretKey hmacKey(byte[] value) {
        if (value == null || value.length < HMAC_KEY_MINIMUM_BYTES) {
            throw new IllegalArgumentException("plan confirmation key configuration is invalid");
        }
        return new SecretKeySpec(value.clone(), "HmacSHA256");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
