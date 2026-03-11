package com.example.clients.auth.dto.profile;

import java.util.List;

public record AuthSyncQuery(
    ProfileQuery profileInfo,
    List<ProfileDeliveryAddressQuery> profileDeliveryInfo
){

}
