package com.telamin.mongoose.example.fivemin;

import com.telamin.fluxtion.runtime.output.MessageSink;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class NamedFeedsFilterHandlerTest {

    private NamedFeedsFilterHandler handler;
    private TestMessageSink testSink;

    @BeforeEach
    public void setup() {
        testSink = new TestMessageSink();

        // Create handler with two accepted feed names
        handler = new NamedFeedsFilterHandler(Set.of("prices", "news"));
        handler.wire(testSink, "testSink");
    }

    @Test
    public void testHandleEvent_ForwardsStringEvents() {
        // When
        handler.handleEvent("testEvent");

        // Then
        Assertions.assertEquals(1, testSink.getMessages().size());
        Assertions.assertEquals("testEvent", testSink.getMessages().get(0));
    }

    @Test
    public void testHandleEvent_IgnoresNonStringEvents() {
        // When
        handler.handleEvent(123);

        // Then
        Assertions.assertTrue(testSink.getMessages().isEmpty());
    }

    /**
     * Simple implementation of MessageSink for testing
     */
    private static class TestMessageSink implements MessageSink<String> {
        private final List<String> messages = new ArrayList<>();

        @Override
        public void accept(String message) {
            messages.add(message);
        }

        public List<String> getMessages() {
            return messages;
        }
    }
}
