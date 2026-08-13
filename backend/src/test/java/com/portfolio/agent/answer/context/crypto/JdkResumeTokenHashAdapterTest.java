package com.portfolio.agent.answer.context.crypto;

import com.portfolio.agent.answer.context.domain.ResumeToken;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdkResumeTokenHashAdapterTest {
    @Test
    void usesHmacAndSupportsCurrentAndPreviousReadKeys() {
        JdkResumeTokenHashAdapter writer = new JdkResumeTokenHashAdapter(
                "current", bytes(32, (byte) 1), "previous", bytes(32, (byte) 2));
        ResumeToken token = ResumeToken.issue();
        ResumeTokenHashPort.HashedToken hash = writer.hash(token);

        assertTrue(writer.matches(token, hash));
        assertFalse(writer.matches(ResumeToken.issue(), hash));

        JdkResumeTokenHashAdapter rotated = new JdkResumeTokenHashAdapter(
                "new", bytes(32, (byte) 3), "current", bytes(32, (byte) 1));
        assertTrue(rotated.matches(token, hash));
    }

    private static byte[] bytes(int length, byte value) {
        byte[] result = new byte[length];
        java.util.Arrays.fill(result, value);
        return result;
    }
}
