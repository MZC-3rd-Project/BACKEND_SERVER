package com.example.clients.product.facade;

import com.fasterxml.jackson.databind.JsonNode;

public interface ProductItemQueryClientFacade {


    JsonNode findItem(Long itemId);
}
