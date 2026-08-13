package com.portfolio.agent.answer.context.gateway;

import com.portfolio.agent.answer.context.domain.ContextHandle;
import com.portfolio.agent.answer.context.domain.ContextSlot;
import com.portfolio.agent.answer.context.domain.ConversationContextEntry;
import com.portfolio.agent.answer.context.domain.ConversationContextMutation;
import com.portfolio.agent.answer.context.domain.ConversationId;
import com.portfolio.agent.answer.context.domain.ResumeToken;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Persistence boundary for typed, short-lived business Context. */
public interface ConversationBusinessContextStore {
    /** Creates the conversation session before answer execution and is idempotent for the same token. */
    void open(ConversationId conversationId, ResumeToken resumeToken, Instant now);

    /** Rotates the session token after a receipt-based first-response recovery. */
    void rotateResumeToken(ConversationId conversationId, ResumeToken replacement, Instant now);

    SaveResult save(
            ConversationId conversationId,
            ResumeToken resumeToken,
            ConversationContextMutation mutation,
            Instant now);

    Optional<ConversationContextEntry> resolve(
            ConversationId conversationId,
            ResumeToken resumeToken,
            ContextHandle contextHandle,
            Instant now);

    List<ConversationContextEntry> list(
            ConversationId conversationId, ResumeToken resumeToken, Instant now);

    Optional<ActiveContext> active(
            ConversationId conversationId,
            ResumeToken resumeToken,
            ContextSlot slot,
            Instant now);

    Optional<ConversationId> findConversation(ResumeToken resumeToken);

    Optional<ConversationContextEntry> resolve(
            ResumeToken resumeToken, ContextHandle contextHandle, Instant now);

    void clear(ResumeToken resumeToken);

    void clear(ConversationId conversationId, ResumeToken resumeToken);

    final class ActiveContext {
        private final ContextSlot slot;
        private final ContextHandle contextHandle;
        private final long revision;

        public ActiveContext(ContextSlot slot, ContextHandle contextHandle, long revision) {
            this.slot = slot;
            this.contextHandle = contextHandle;
            this.revision = revision;
        }

        public ContextSlot getSlot() { return slot; }
        public ContextHandle getContextHandle() { return contextHandle; }
        public long getRevision() { return revision; }
    }

    final class SaveResult {
        private final ConversationContextEntry entry;
        private final boolean activeAdvanced;
        private final long activeRevision;

        public SaveResult(
                ConversationContextEntry entry, boolean activeAdvanced, long activeRevision) {
            this.entry = entry;
            this.activeAdvanced = activeAdvanced;
            this.activeRevision = activeRevision;
        }

        public ConversationContextEntry getEntry() { return entry; }
        public boolean isActiveAdvanced() { return activeAdvanced; }
        public long getActiveRevision() { return activeRevision; }
    }
}
