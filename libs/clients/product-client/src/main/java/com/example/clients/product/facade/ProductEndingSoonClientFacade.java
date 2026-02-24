package com.example.clients.product.facade;

import com.fasterxml.jackson.databind.JsonNode;

public interface ProductEndingSoonClientFacade extends ProductItemQueryClientFacade {

    JsonNode findItemsEndingSoon();
}
