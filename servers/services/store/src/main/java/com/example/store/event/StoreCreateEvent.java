package com.example.store.event;

import com.example.event.DomainEvent;
import com.example.store.entity.AddressType;
import com.example.store.entity.ContactType;
import com.example.store.entity.StoreImage;
import com.example.store.entity.StoreStatus;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class StoreCreateEvent extends DomainEvent {

    private final Long storeId;
    private final Long userId;
    private final String storeName;
    private final StoreStatus status;
    private final String address;
    private final AddressType addressType;
    private final boolean isDefault;
    private final ContactType contactType;
    private final String contactValue;
    private final boolean isPrimary;
    private final String description;
    private final List<StoreImage> storeImages;


    public StoreCreateEvent(Long storeId,
                            Long userId,
                            String storeName,
                            StoreStatus status,
                            String address,
                            AddressType addressType, //     MAIN, PICKUP, RETURN, WAREHOUSE
                            boolean isDefault,
                            ContactType contactType, //  PHONE, EMAIL,
                            String contactValue,
                            boolean isPrimary,
                            String description,
                            List<StoreImage> storeImages

    ) {
        super("store-event");
        this.storeId = storeId;
        this.userId = userId;
        this.storeName = storeName;
        this.status = status;
        this.addressType = addressType;
        this.address = address;
        this.isDefault = isDefault;
        this.contactType = contactType;
        this.contactValue = contactValue;
        this.isPrimary = isPrimary;
        this.description = description;
        this.storeImages = storeImages;
    }

    @Override
    public String getEventTypeName() {
        return "StoreCreated";
    }

    @Override
    public Map<String, Object> getPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();

        payload.put("storeId", storeId);
        payload.put("userId", userId);
        payload.put("storeName", storeName);
        payload.put("status", status);
        payload.put("description", description);

        // address
        Map<String, Object> addressPayload = new LinkedHashMap<>();
        addressPayload.put("address", address);
        addressPayload.put("addressType", addressType);
        addressPayload.put("isDefault", isDefault);
        payload.put("address", addressPayload);

        // contact
        Map<String, Object> contactPayload = new LinkedHashMap<>();
        contactPayload.put("contactType", contactType);
        contactPayload.put("contactValue", contactValue);
        contactPayload.put("isPrimary", isPrimary);
        payload.put("contact", contactPayload);

        // images
        List<Map<String, Object>> imagesPayload = storeImages.stream()
            .map(img -> {
                Map<String, Object> image = new LinkedHashMap<>();
                image.put("imageType", img.getImageType());
                image.put("mediaId", img.getMediaId());
                image.put("sortOrder", img.getSortOrder());
                return image;
            })
            .toList();

        payload.put("images", imagesPayload);
        return payload;

    }
}
