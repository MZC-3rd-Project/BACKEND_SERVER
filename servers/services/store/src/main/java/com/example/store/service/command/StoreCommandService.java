package com.example.store.service.command;

import com.example.core.exception.BusinessException;
import com.example.core.exception.CommonErrorCode;
import com.example.core.exception.TechnicalException;
import com.example.event.EventMetadata;
import com.example.event.EventPublisher;
import com.example.store.dto.image.StoreImageResponse;
import com.example.store.dto.request.StoreCreateRequest;
import com.example.store.dto.request.StoreUpdateRequest;
import com.example.store.dto.response.StoreCreateResponse;
import com.example.store.dto.response.StoreDeleteResponse;
import com.example.store.dto.response.StoreUpdateResponse;
import com.example.store.entity.*;
import com.example.store.event.StoreCreateEvent;
import com.example.store.exception.StoreErrorCode;
import com.example.store.repository.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

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
    private final EventPublisher eventPublisher;

    //동기 api
    private final StoreMediaReferenceService storeMediaReferenceService;

    @Transactional
    public StoreCreateResponse create(Long userId, StoreCreateRequest request){
        if(storesRepository.existsByUserIdAndDeletedAtIsNull(userId)){
            throw new BusinessException(StoreErrorCode.STORE_ALREADY_EXISTS);
        }

        Stores store = Stores.of(userId, request.storeName());
        saveStore(store);
        StoreAddress storeAddress = saveStoreAddress(store, request);
        StoreContact storeContact = saveStoreContact(store, request);

        StoreProfile storeProfile = null;

        if (request.description() != null) {
            storeProfile = saveStoreProfile(store, request);
        }
        List<StoreImage> storeImages = List.of();
        if (request.images() != null && !request.images().isEmpty()) {
            storeImages = saveStoreImages(store, request);
        }

//        storeMediaReferenceService.syncStoreImagesOnCreate(store.getId(), request.images());
        eventPublisher.publish(
            new StoreCreateEvent(
                store.getId(),
                store.getUserId(),
                store.getStoreName(),
                store.getStatus(),
                storeAddress.getAddress(),
                storeAddress.getAddressType(),
                storeAddress.getIsDefault(),
                storeContact.getContactType(),
                storeContact.getContactValue(),
                storeContact.getIsPrimary(),
                storeProfile != null ? storeProfile.getDescription() : null,
                storeImages
            ),
            EventMetadata.of("STORE", String.valueOf(store.getId()))
        );

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

    private StoreAddress saveStoreAddress(Stores store, StoreCreateRequest request) {
        try {
            StoreAddress result = storeAddressRepository.save(StoreAddress.of(store, request.addressType(), request.address()));
            log.info("[StoreAddress] 저장 성공 - storeId: {}, addressType: {}", store.getId(), request.addressType());
            return StoreAddress.of(result.getAddressType(), result.getAddress());
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

    private StoreContact saveStoreContact(Stores store, StoreCreateRequest request) {
        try {
            StoreContact result = storeContactRepository.save(StoreContact.of(store, request.contactType(), request.contactValue(), true));
            log.info("[StoreContact] 저장 성공 - storeId: {}, contactType: {}", store.getId(), request.contactType());
            return StoreContact.of(result.getContactType(), result.getContactValue(),true);
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

    private StoreProfile saveStoreProfile(Stores store, StoreCreateRequest request) {
        try {
            StoreProfile result = storeProfileRepository.save(StoreProfile.of(store, request.description()));
            log.info("[StoreProfile] 저장 성공 - storeId: {}", store.getId());
            return StoreProfile.of(result.getDescription());
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

    private List<StoreImage> saveStoreImages(Stores store, StoreCreateRequest request) {
        try {
            List<StoreImage> result = storeImageRepository.saveAll(request.images().stream()
                .map(img -> StoreImage.of(store, img.imageType(), img.mediaId(), img.sortOrder()))
                .toList()
            );
            log.info("[StoreImage] 저장 성공 - storeId: {}, imageCount: {}", store.getId(), request.images().size());
            return result;
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

    public StoreUpdateResponse update(Long userId, Long storeId, StoreUpdateRequest request){
        Stores findStore = storesRepository.findByIdAndDeletedAtIsNull(storeId)
            .orElseThrow(() -> new BusinessException(StoreErrorCode.STORE_NOT_FOUND));

        validatorOwner(findStore.getUserId(), userId);

        findStore.updateStore(request.storeName(), request.status());

        StoreAddress storeAddress = storeAddressRepository.findByStoreIdAndIsDefaultTrueAndDeletedAtIsNull(storeId)
            .orElseThrow(() -> new BusinessException(StoreErrorCode.ADDRESS_NOT_FOUND));
        storeAddress.updateStoreAddress(request.address(), request.addressType());

        storeProfileRepository.findByStoreId(storeId)
            .ifPresentOrElse(
                p ->p.updateDescription(request.description()),
                () -> storeProfileRepository.save(StoreProfile.of(findStore, request.description()))
            );

        StoreContact storeContact = storeContactRepository.findByStoreIdAndIsPrimaryTrueAndDeletedAtIsNull(storeId)
            .orElseThrow(() -> new BusinessException(StoreErrorCode.CONTACT_NOT_FOUND));
        storeContact.updateStoreContact(request.contactValue(), request.contactType());
        // 이벤트 발행 추가
        if (request.images() != null) {
            storeImageRepository.saveAll(
                request.images()
                    .stream()
                    .map(img -> StoreImage.of(img.imageType(), img.mediaId(), img.sortOrder()))
                    .toList()
            );
        }
        List<StoreUpdateResponse.StoreImageResponse> imgList = Optional.ofNullable(request.images())
            .orElse(List.of())
            .stream()
            .map(img -> new StoreUpdateResponse.StoreImageResponse(img.imageType(), img.sortOrder(), img.mediaId()))
            .toList();

        storeMediaReferenceService.syncStoreImagesOnUpdate(storeId, request.images());

        return new StoreUpdateResponse(
            findStore.getId(),
            findStore.getUserId(),
            findStore.getStoreName(),
            findStore.getStatus(),
            request.description(),
            storeAddress.getAddress(),
            storeAddress.getAddressType(),
            storeContact.getContactValue(),
            storeContact.getContactType(),
            imgList
        );
    }

    private void validatorOwner(Long storeId, Long userId){
        if(!storeId.equals(userId)){
            throw new BusinessException(StoreErrorCode.STORE_ACCESS_DENIED);
        }
    }

    public StoreDeleteResponse delete(Long userId,Long storeId){
        Stores stores = storesRepository.findByIdAndDeletedAtIsNull(storeId)
            .orElseThrow(() -> new BusinessException(StoreErrorCode.STORE_NOT_FOUND));

        validatorOwner(stores.getUserId(), userId);

        StoreAddress storeAddress = storeAddressRepository.findById(storeId)
            .orElseThrow(() -> new BusinessException(StoreErrorCode.ADDRESS_NOT_FOUND));
        StoreContact storeContact = storeContactRepository.findById(storeId)
            .orElseThrow(() -> new BusinessException(StoreErrorCode.CONTACT_NOT_FOUND));
        StoreImage storeImage = storeImageRepository.findById(storeId)
            .orElseThrow(() -> new BusinessException(StoreErrorCode.IMAGE_NOT_FOUND));
        StoreProfile storeProfile = storeProfileRepository.findByStoreId(storeId)
            .orElseThrow(() -> new BusinessException(StoreErrorCode.PROFILE_NOT_FOUND));


        stores.softDelete();
        storeAddress.softDelete();
        storeContact.softDelete();
        storeImage.softDelete();
        storeProfile.softDelete();

        storeMediaReferenceService.clearStoreImageLinks(storeId);

        return StoreDeleteResponse.of(storeId);
    }

}
