package com.example.clients.auth.facade;

import com.fasterxml.jackson.databind.JsonNode;

public interface AuthItemQueryClientFacade {

    JsonNode findItem(Long itemId);
}
