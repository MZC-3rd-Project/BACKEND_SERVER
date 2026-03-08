package com.example.store.service.command;

import com.example.core.exception.BusinessException;
import com.example.core.exception.CommonErrorCode;
import com.example.core.exception.TechnicalException;
import com.example.store.dto.request.StoreCreateRequest;
import com.example.store.dto.response.StoreCreateResponse;
import com.example.store.entity.*;
import com.example.store.exception.StoreErrorCode;
import com.example.store.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class StoreCommandService {

    private final StoresRepository storesRepository;
    private final StoreProfileRepository storeProfileRepository;
    private final StoreImageRepository storeImageRepository;
    private final StoreContactRepository storeContactRepository;
    private final StoreAddressRepository storeAddressRepository;

    @Transactional
    public StoreCreateResponse create(Long userId, StoreCreateRequest request){
        if(storesRepository.existsByUserIdAndDeletedAtIsNull(userId)){
            throw new BusinessException(StoreErrorCode.STORE_ALREADY_EXISTS);
        }

        Stores store = Stores.of(userId, request.storeName());
        saveStore(store);
        saveStoreAddress(store, request);
        saveStoreContact(store, request);

        if (request.description() != null) {
            saveStoreProfile(store, request);
        }
        if (request.images() != null && !request.images().isEmpty()) {
            saveStoreImages(store, request);
        }

        return StoreCreateResponse.of(
            store.getId(),
            store.getUserId(),
            store.getStoreName(),
            store.getStatus()
        );

    }

    private void saveStore(Stores store) {
        try {
            storesRepository.save(store);
            log.info("[Store] 저장 성공 - userId: {}, storeName: {}", store.getUserId(), store.getStoreName());
        } catch (BusinessException e) {
            log.warn("[Store] 저장 실패 - code: {}, message: {}, userId: {}",
                e.getErrorCode().getCode(), e.getErrorCode().getMessage(), store.getUserId());
            throw new BusinessException(StoreErrorCode.STORE_ALREADY_EXISTS);
        } catch (Exception e) {
            log.error("[Store] 저장 실패 - code: {}, message: {}, userId: {}",
                CommonErrorCode.INTERNAL_ERROR.getCode(), CommonErrorCode.INTERNAL_ERROR.getMessage(), store.getUserId(), e);
            throw new TechnicalException(CommonErrorCode.INTERNAL_ERROR);
        }
    }

    private void saveStoreAddress(Stores store, StoreCreateRequest request) {
        try {
            storeAddressRepository.save(StoreAddress.of(store, request.addressType(), request.address()));
            log.info("[StoreAddress] 저장 성공 - storeId: {}, addressType: {}", store.getId(), request.addressType());
        } catch (BusinessException e) {
            log.warn("[StoreAddress] 저장 실패 - code: {}, message: {}, storeId: {}",
                e.getErrorCode().getCode(), e.getErrorCode().getMessage(), store.getId());
            throw new BusinessException(StoreErrorCode.ADDRESS_ALREADY_EXISTS);
        } catch (Exception e) {
            log.error("[StoreAddress] 저장 실패 - code: {}, message: {}, storeId: {}",
                CommonErrorCode.INTERNAL_ERROR.getCode(), CommonErrorCode.INTERNAL_ERROR.getMessage(), store.getId(), e);
            throw new TechnicalException(CommonErrorCode.INTERNAL_ERROR);
        }
    }

    private void saveStoreContact(Stores store, StoreCreateRequest request) {
        try {
            storeContactRepository.save(StoreContact.of(store, request.contactType(), request.contactValue(), true));
            log.info("[StoreContact] 저장 성공 - storeId: {}, contactType: {}", store.getId(), request.contactType());
        } catch (BusinessException e) {
            log.warn("[StoreContact] 저장 실패 - code: {}, message: {}, storeId: {}",
                e.getErrorCode().getCode(), e.getErrorCode().getMessage(), store.getId());
            throw new BusinessException(StoreErrorCode.CONTACT_ALREADY_EXISTS);
        } catch (Exception e) {
            log.error("[StoreContact] 저장 실패 - code: {}, message: {}, storeId: {}",
                CommonErrorCode.INTERNAL_ERROR.getCode(), CommonErrorCode.INTERNAL_ERROR.getMessage(), store.getId(), e);
            throw new TechnicalException(CommonErrorCode.INTERNAL_ERROR);
        }
    }

    private void saveStoreProfile(Stores store, StoreCreateRequest request) {
        try {
            storeProfileRepository.save(StoreProfile.of(store, request.description()));
            log.info("[StoreProfile] 저장 성공 - storeId: {}", store.getId());
        } catch (BusinessException e) {
            log.warn("[StoreProfile] 저장 실패 - code: {}, message: {}, storeId: {}",
                e.getErrorCode().getCode(), e.getErrorCode().getMessage(), store.getId());
            throw new BusinessException(StoreErrorCode.PROFILE_ALREADY_EXISTS);
        } catch (Exception e) {
            log.error("[StoreProfile] 저장 실패 - code: {}, message: {}, storeId: {}",
                CommonErrorCode.INTERNAL_ERROR.getCode(), CommonErrorCode.INTERNAL_ERROR.getMessage(), store.getId(), e);
            throw new TechnicalException(CommonErrorCode.INTERNAL_ERROR);
        }
    }

    private void saveStoreImages(Stores store, StoreCreateRequest request) {
        try {
            storeImageRepository.saveAll(request.images().stream()
                .map(img -> StoreImage.of(store, img.imageType(), img.mediaId(), img.sortOrder()))
                .toList()
            );
            log.info("[StoreImage] 저장 성공 - storeId: {}, imageCount: {}", store.getId(), request.images().size());
        } catch (BusinessException e) {
            log.warn("[StoreImage] 저장 실패 - code: {}, message: {}, storeId: {}",
                e.getErrorCode().getCode(), e.getErrorCode().getMessage(), store.getId());
            throw new BusinessException(StoreErrorCode.IMAGE_UPLOAD_FAILED);
        } catch (Exception e) {
            log.error("[StoreImage] 저장 실패 - code: {}, message: {}, storeId: {}",
                CommonErrorCode.INTERNAL_ERROR.getCode(), CommonErrorCode.INTERNAL_ERROR.getMessage(), store.getId(), e);
            throw new TechnicalException(CommonErrorCode.INTERNAL_ERROR);
        }
    }

}
