package com.example.event.inbox;

import com.example.event.consumer.EventMessageProcessor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InboxHandlerAdaptersTest {

    @Test
    void merge_adaptsAnnotatedProcessor() throws Exception {
        RecordingProcessor processor = new RecordingProcessor();

        List<InboxEventHandler> handlers = InboxHandlerAdapters.merge(List.of(), List.of(processor));

        assertThat(handlers).hasSize(1);
        InboxEventHandler handler = handlers.getFirst();
        assertThat(handler.consumerName()).isEqualTo("profile-user-events-consumer");
        assertThat(handler.supports("UserCreated")).isTrue();

        handler.handle("evt-1", "UserCreated", "{\"eventId\":\"evt-1\"}");

        assertThat(processor.lastMessage).isEqualTo("{\"eventId\":\"evt-1\"}");
        assertThat(processor.lastEventId).isEqualTo("evt-1");
        assertThat(processor.lastEventType).isEqualTo("UserCreated");
    }

    @Test
    void merge_keepsExplicitHandlerBeforeAdaptedProcessor() {
        InboxEventHandler explicitHandler = new InboxEventHandler() {
            @Override
            public String consumerName() {
                return "profile-user-events-consumer";
            }

            @Override
            public boolean supports(String eventType) {
                return true;
            }

            @Override
            public void handle(String eventId, String eventType, String payload) {
            }
        };

        List<InboxEventHandler> handlers = InboxHandlerAdapters.merge(
                List.of(explicitHandler),
                List.of(new RecordingProcessor())
        );

        assertThat(handlers).hasSize(2);
        assertThat(handlers.getFirst()).isSameAs(explicitHandler);
    }

    @Test
    void merge_ignoresUnannotatedProcessor() {
        List<InboxEventHandler> handlers = InboxHandlerAdapters.merge(List.of(), List.of(new PlainProcessor()));

        assertThat(handlers).isEmpty();
    }

    @InboxConsumerBinding(consumerName = "profile-user-events-consumer")
    private static final class RecordingProcessor implements EventMessageProcessor {

        private String lastMessage;
        private String lastEventId;
        private String lastEventType;

        @Override
        public boolean supports(String eventType) {
            return "UserCreated".equals(eventType);
        }

        @Override
        public void process(String message, String eventId, String eventType) {
            this.lastMessage = message;
            this.lastEventId = eventId;
            this.lastEventType = eventType;
        }
    }

    private static final class PlainProcessor implements EventMessageProcessor {

        @Override
        public boolean supports(String eventType) {
            return false;
        }

        @Override
        public void process(String message, String eventId, String eventType) {
        }
    }
}
