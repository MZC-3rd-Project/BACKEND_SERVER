package com.example.hotdeal.scheduler;

import com.example.hotdeal.entity.HotDeal;
import com.example.hotdeal.entity.HotDealStatus;
import com.example.hotdeal.repository.HotDealRepository;
import com.example.hotdeal.service.QueueService;
import com.example.hotdeal.service.QueueSseEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueueAdmissionSchedulerTest {

    @Mock
    private QueueService queueService;

    @Mock
    private QueueSseEventPublisher queueSseEventPublisher;

    @Mock
    private HotDealRepository hotDealRepository;

    @InjectMocks
    private QueueAdmissionScheduler queueAdmissionScheduler;

    @Test
    void admitUsers_publishesAdmittedUsersPerActiveDeal() {
        HotDeal first = mock(HotDeal.class);
        HotDeal second = mock(HotDeal.class);
        when(first.getId()).thenReturn(10L);
        when(second.getId()).thenReturn(20L);
        ReflectionTestUtils.setField(queueAdmissionScheduler, "maxConcurrentAdmissions", 7);
        when(hotDealRepository.findByStatus(HotDealStatus.ACTIVE)).thenReturn(List.of(first, second));
        when(queueService.admitUsersByAvailableStock(10L, 7)).thenReturn(Set.of(1L, 2L));
        when(queueService.admitUsersByAvailableStock(20L, 7)).thenReturn(Set.of());

        queueAdmissionScheduler.admitUsers();

        verify(queueSseEventPublisher).publishAdmittedUsers(10L, Set.of(1L, 2L));
        verify(queueSseEventPublisher).publishAdmittedUsers(20L, Set.of());
    }
}
