package com.example.clients.auth.facade;

import com.example.clients.auth.dto.AuthItemSummary;

public interface AuthItemSummaryClientFacade {

    AuthItemSummary findItemSummary(Long itemId);
}
