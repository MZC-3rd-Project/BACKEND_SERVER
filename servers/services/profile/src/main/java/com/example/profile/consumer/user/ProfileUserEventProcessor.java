package com.example.profile.consumer.user;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.event.consumer.AbstractIdempotentEventSpecProcessor;
import com.example.event.consumer.EventEnvelope;
import com.example.event.consumer.EventSpec;
import com.example.event.inbox.InboxConsumerBinding;
import com.example.profile.consumer.user.dto.UserCreatedEventDto;
import com.example.profile.consumer.user.dto.UserEmailChangedEventDto;
import com.example.profile.consumer.user.dto.UserWithdrawnEventDto;
import com.example.profile.service.command.ProfileProjectionSyncService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

@Component
@InboxConsumerBinding(consumerName = ProfileUserEventProcessor.CONSUMER_NAME)
public class ProfileUserEventProcessor extends AbstractIdempotentEventSpecProcessor {

    public static final String CONSUMER_NAME = "profile-user-events-consumer";
    private static final String IDEMPOTENT_EVENT_TYPE = "USER_EVENT";
    public static final String USER_CREATED_EVENT_TYPE = "UserCreated";
    public static final String USER_EMAIL_CHANGED_EVENT_TYPE = "UserEmailChanged";
    public static final String USER_WITHDRAWN_EVENT_TYPE = "UserWithdrawn";
    private final Map<String, EventSpec<? extends EventEnvelope>> eventSpecs;

    public ProfileUserEventProcessor(
            IdempotentConsumerService idempotentConsumerService,
            ProfileProjectionSyncService profileProjectionSyncService
    ) {
        super(idempotentConsumerService);
        this.eventSpecs = Map.of(
                USER_CREATED_EVENT_TYPE,
                EventSpec.of(
                        UserCreatedEventDto.class,
                        this::isValidUserCreated,
                        current -> profileProjectionSyncService.upsertFromUserCreated(
                                current.userId(),
                                current.email(),
                                current.nickname()
                        )
                ),
                USER_EMAIL_CHANGED_EVENT_TYPE,
                EventSpec.of(
                        UserEmailChangedEventDto.class,
                        this::isValidUserEmailChanged,
                        current -> profileProjectionSyncService.applyUserEmailChanged(current.userId(), current.newEmail())
                ),
                USER_WITHDRAWN_EVENT_TYPE,
                EventSpec.of(
                        UserWithdrawnEventDto.class,
                        this::isValidUserWithdrawn,
                        current -> profileProjectionSyncService.withdrawProjection(current.userId())
                )
        );
    }

    @Override
    protected String idempotentEventType() {
        return IDEMPOTENT_EVENT_TYPE;
    }

    @Override
    protected Map<String, EventSpec<? extends EventEnvelope>> eventSpecs() {
        return eventSpecs;
    }

    private boolean isValidUserCreated(UserCreatedEventDto current) {
        return current.userId() != null
                && StringUtils.hasText(current.email())
                && StringUtils.hasText(current.nickname());
    }

    private boolean isValidUserEmailChanged(UserEmailChangedEventDto current) {
        return current.userId() != null && StringUtils.hasText(current.newEmail());
    }

    private boolean isValidUserWithdrawn(UserWithdrawnEventDto current) {
        return current.userId() != null;
    }
}
