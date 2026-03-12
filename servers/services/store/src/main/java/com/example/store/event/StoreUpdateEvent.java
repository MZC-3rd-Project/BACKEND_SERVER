package com.example.store.event;

import com.example.event.DomainEvent;
import com.example.store.dto.response.StoreUpdateResponse;
import com.example.store.entity.AddressType;
import com.example.store.entity.ContactType;
import com.example.store.entity.StoreStatus;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class StoreUpdateEvent extends DomainEvent {

    private final Long storeId;
    private final Long userId;
    private final String storeName;
    private final StoreStatus status;
    private final String description;
    private final String address;
    private final AddressType addressType;
    private final String contactValue;
    private final ContactType contactType;
    private final List<StoreUpdateResponse.StoreImageResponse> images;

    public StoreUpdateEvent(
        Long storeId,
        Long userId,
        String storeName,
        StoreStatus status,
        String description,
        String address,
        AddressType addressType,
        String contactValue,
        ContactType contactType,
        List<StoreUpdateResponse.StoreImageResponse> images
    ) {
        super("store-event");
        this.storeId = storeId;
        this.userId = userId;
        this.storeName = storeName;
        this.status = status;
        this.description = description;
        this.address = address;
        this.addressType = addressType;
        this.contactValue = contactValue;
        this.contactType = contactType;
        this.images = images == null ? List.of() : List.copyOf(images);
    }

    @Override
    public String getEventTypeName() {
        return "StoreUpdated";
    }

    @Override
    public Map<String, Object> getPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("storeId", storeId);
        payload.put("userId", userId);
        payload.put("storeName", storeName);
        payload.put("status", status);
        payload.put("description", description);
        Map<String, Object> addressPayload = new LinkedHashMap<>();
        addressPayload.put("address", address);
        addressPayload.put("addressType", addressType);
        payload.put("address", addressPayload);

        Map<String, Object> contactPayload = new LinkedHashMap<>();
        contactPayload.put("contactValue", contactValue);
        contactPayload.put("contactType", contactType);
        payload.put("contact", contactPayload);

        payload.put("images", images.stream()
            .map(image -> Map.of(
                "mediaId", image.mediaId(),
                "imageType", image.imageType(),
                "sortOrder", image.sortOrder()
            ))
            .toList());
        return payload;
    }
}
