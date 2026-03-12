package com.example.store.event;

import com.example.event.DomainEvent;
import com.example.store.entity.AddressType;
import com.example.store.entity.ContactType;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class StoreDeleteEvent extends DomainEvent {


    private final Long storeId;
    private final Long userId;
    private final AddressType addressType;
    private final String address;
    private final ContactType contactType;
    private final String contactValue;
    private final Long mediaId;
    private final Integer sortOrder;
    private final String description;


    public StoreDeleteEvent(Long storeId,
                            Long userId,
                            AddressType addressType,
                            String address,
                            ContactType contactType,
                            String contactValue,
                            Long mediaId,
                            Integer sortOrder,
                            String description) {
        super("store-event");
        this.storeId = storeId;
        this.userId = userId;
        this.addressType = addressType;
        this.address = address;
        this.contactType = contactType;
        this.contactValue = contactValue;
        this.mediaId = mediaId;
        this.sortOrder = sortOrder;
        this.description = description;

    }

    @Override
    public String getEventTypeName() {
        return "StoreDeleted";
    }

    @Override
    public Map<String, Object> getPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();

        payload.put("storeId", storeId);
        payload.put("userId", userId);
        payload.put(
            "address",
            Map.of(
                "addressType", addressType,
                "address", address
            )
        );
        payload.put(
            "contact",
            Map.of(
                "contactType", contactType,
                "contactValue", contactValue
            )
        );
        payload.put(
            "image",
            Map.of(
                "mediaId", mediaId,
                "sortOrder", sortOrder
            )
        );
        payload.put(
            "profile",
            Map.of(
                "description", description
            )
        );

        return payload;
    }
}
