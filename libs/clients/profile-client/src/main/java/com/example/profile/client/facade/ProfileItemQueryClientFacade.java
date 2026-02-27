package com.example.profile.client.facade;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public interface ProfileItemQueryClientFacade {

    List<JsonNode> findProfileList();
}
