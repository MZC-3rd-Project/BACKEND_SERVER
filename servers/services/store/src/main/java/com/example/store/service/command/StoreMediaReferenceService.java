package com.example.store.service.command;

import com.example.clients.media.dto.MediaLinksSyncCommand;
import com.example.clients.media.dto.MediaOwnerType;
import com.example.clients.media.dto.MediaUsageType;
import com.example.clients.media.exception.InvalidMediaReferenceException;
import com.example.clients.media.exception.MediaClientException;
import com.example.clients.media.facade.MediaClientFacade;
import com.example.store.dto.request.StoreCreateRequest;
import com.example.store.dto.request.StoreUpdateRequest;
import com.example.store.entity.ImageType;
import com.example.store.exception.StoreErrorCode;
import com.example.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StoreMediaReferenceService {

    private final MediaClientFacade mediaClientFacade;

    /**
     * 가게 생성 시 이미지 링크 동기화
     * - images 배열에서 THUMBNAIL, GALLERY 타입별로 mediaId 목록을 추출해 media 서비스에 전달
     * - mediaId가 null인 항목은 전송하지 않음 (생성 시에는 null 이미지 의미 없음)
     */
    public void syncStoreImagesOnCreate(Long storeId, List<StoreCreateRequest.StoreImageRequest> images) {
        if (images == null || images.isEmpty()) {
            return;
        }

        // ImageType별로 mediaId 그룹핑 (null 제외)
        Map<ImageType, List<Long>> mediaIdsByType = images.stream()
            .filter(img -> img.mediaId() != null)
            .collect(Collectors.groupingBy(
                StoreCreateRequest.StoreImageRequest::imageType,
                Collectors.mapping(StoreCreateRequest.StoreImageRequest::mediaId, Collectors.toList())
            ));

        List<MediaLinksSyncCommand.MediaUsageSet> usageSets = List.of(
            new MediaLinksSyncCommand.MediaUsageSet(
                MediaUsageType.THUMBNAIL,
                mediaIdsByType.getOrDefault(ImageType.THUMBNAIL, List.of())
            ),
            new MediaLinksSyncCommand.MediaUsageSet(
                MediaUsageType.GALLERY,
                mediaIdsByType.getOrDefault(ImageType.GALLERY, List.of())
            )
        );

        MediaLinksSyncCommand command = new MediaLinksSyncCommand(
            MediaOwnerType.STORE,
            storeId,
            usageSets
        );

        doSyncLinks(storeId, command);
    }

    /**
     * 가게 수정 시 이미지 링크 동기화
     * - mediaId가 null → 해당 타입 이미지 삭제 신호 → 빈 리스트로 전달
     * - mediaId가 존재 → 이미지 추가/변경 → 해당 mediaId 전달
     * - images 자체가 null → 이미지 변경 없음 → 동기화 생략
     */
    public void syncStoreImagesOnUpdate(Long storeId, List<StoreUpdateRequest.StoreImageRequest> images) {
        if (images == null) {
            // images 자체가 null이면 이미지 수정 요청이 없는 것 → 동기화 생략
            return;
        }

        // ImageType별로 mediaId 그룹핑 (null이면 빈 리스트 → 삭제 신호)
        Map<ImageType, List<Long>> mediaIdsByType = images.stream()
            .collect(Collectors.groupingBy(
                StoreUpdateRequest.StoreImageRequest::imageType,
                Collectors.mapping(
                    StoreUpdateRequest.StoreImageRequest::mediaId, // null 포함
                    Collectors.toList()
                )
            ));

        // null mediaId는 제거 후 전달 (null = 삭제 신호이므로 빈 리스트가 됨)
        List<Long> thumbnailIds = mediaIdsByType.getOrDefault(ImageType.THUMBNAIL, List.of())
            .stream()
            .filter(Objects::nonNull)
            .toList();

        List<Long> galleryIds = mediaIdsByType.getOrDefault(ImageType.GALLERY, List.of())
            .stream()
            .filter(Objects::nonNull)
            .toList();

        List<MediaLinksSyncCommand.MediaUsageSet> usageSets = List.of(
            new MediaLinksSyncCommand.MediaUsageSet(MediaUsageType.THUMBNAIL, thumbnailIds),
            new MediaLinksSyncCommand.MediaUsageSet(MediaUsageType.GALLERY,   galleryIds)
        );

        MediaLinksSyncCommand command = new MediaLinksSyncCommand(
            MediaOwnerType.STORE,
            storeId,
            usageSets
        );

        doSyncLinks(storeId, command);
    }

    /**
     * 가게 삭제 시 이미지 링크 전체 해제
     */
    public void clearStoreImageLinks(Long storeId) {
        MediaLinksSyncCommand command = new MediaLinksSyncCommand(
            MediaOwnerType.STORE,
            storeId,
            List.of() // 빈 리스트 = 전체 해제
        );
        doSyncLinks(storeId, command);
    }

    private void doSyncLinks(Long storeId, MediaLinksSyncCommand command) {
        try {
            mediaClientFacade.syncLinks(command);
            log.info("[StoreMedia] 이미지 링크 동기화 성공 - storeId: {}", storeId);
        } catch (InvalidMediaReferenceException e) {
            log.warn("[StoreMedia] 유효하지 않은 mediaId - storeId: {}", storeId, e);
            throw new BusinessException(StoreErrorCode.IMAGE_UPLOAD_FAILED, e);
        } catch (MediaClientException e) {
            log.error("[StoreMedia] media 서비스 통신 오류 - storeId: {}", storeId, e);
            throw new BusinessException(StoreErrorCode.IMAGE_UPLOAD_FAILED, e);
        }
    }
}
