package com.example.event.inbox;

import com.example.event.consumer.EventMessageProcessor;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;

import java.util.ArrayList;
import java.util.List;

final class InboxHandlerAdapters {

    private InboxHandlerAdapters() {
    }

    static List<InboxEventHandler> merge(
            List<InboxEventHandler> handlers,
            List<EventMessageProcessor> processors
    ) {
        List<InboxEventHandler> merged = new ArrayList<>();
        if (handlers != null) {
            merged.addAll(handlers);
        }
        if (processors == null) {
            return List.copyOf(merged);
        }

        for (EventMessageProcessor processor : processors) {
            InboxConsumerBinding binding = findBinding(processor);
            if (binding == null) {
                continue;
            }
            merged.add(new ProcessorBackedInboxEventHandler(binding.consumerName(), processor));
        }
        return List.copyOf(merged);
    }

    private static InboxConsumerBinding findBinding(EventMessageProcessor processor) {
        Class<?> userClass = ClassUtils.getUserClass(processor);
        return AnnotatedElementUtils.findMergedAnnotation(userClass, InboxConsumerBinding.class);
    }
}
