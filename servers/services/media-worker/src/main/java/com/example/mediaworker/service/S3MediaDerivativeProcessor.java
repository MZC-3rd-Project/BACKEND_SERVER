package com.example.mediaworker.service;

import com.sksamuel.scrimage.ImmutableImage;
import com.sksamuel.scrimage.webp.WebpWriter;
import com.example.mediaworker.config.MediaWorkerDerivativeProperties;
import com.example.mediaworker.config.MediaWorkerS3Properties;
import com.example.mediaworker.entity.MediaDerivative;
import com.example.mediaworker.entity.MediaDerivativeProfile;
import com.example.mediaworker.entity.MediaDerivativeTask;
import com.example.mediaworker.entity.MediaFileRecord;
import com.example.mediaworker.entity.MediaFileStatus;
import com.example.mediaworker.repository.MediaDerivativeRepository;
import com.example.mediaworker.repository.MediaFileRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Locale;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "media.worker.derivative", name = "enabled", havingValue = "true", matchIfMissing = true)
public class S3MediaDerivativeProcessor implements MediaDerivativeProcessor {

    private final S3Client mediaWorkerS3Client;
    private final MediaWorkerS3Properties mediaWorkerS3Properties;
    private final MediaWorkerDerivativeProperties derivativeProperties;
    private final MediaFileRecordRepository mediaFileRecordRepository;
    private final MediaDerivativeRepository mediaDerivativeRepository;

    @Override
    @Transactional
    public void process(MediaDerivativeTask task) {
        if (!derivativeProperties.isEnabled()) {
            return;
        }

        MediaFileRecord mediaFileRecord = mediaFileRecordRepository.findById(task.getMediaId())
                .orElseThrow(() -> new NonRetriableMediaProcessingException("media file not found: " + task.getMediaId()));
        if (!mediaFileRecord.isReadyForDerivative()) {
            throw new NonRetriableMediaProcessingException("media file is not ready for derivative: " + task.getMediaId());
        }

        ResponseBytes<GetObjectResponse> sourceBytes = mediaWorkerS3Client.getObjectAsBytes(
                GetObjectRequest.builder()
                        .bucket(mediaFileRecord.getBucketName())
                        .key(mediaFileRecord.getObjectKey())
                        .build()
        );

        BufferedImage sourceImage = decodeImage(sourceBytes.asByteArray());
        DerivativeImageResult derivativeImage = toThumbnailImage(sourceImage);
        String objectKey = buildDerivativeObjectKey(mediaFileRecord.getObjectKey(), task, derivativeImage.extension());
        uploadDerivative(mediaFileRecord.getBucketName(), objectKey, derivativeImage);

        String urlSnapshot = buildBaseUrl(objectKey);
        MediaDerivative derivative = mediaDerivativeRepository
                .findByMediaIdAndDerivativeProfileAndMediaVersion(
                        task.getMediaId(),
                        task.getDerivativeProfile(),
                        task.getMediaVersion()
                )
                .orElseGet(() -> MediaDerivative.createReady(
                        task.getMediaId(),
                        task.getDerivativeProfile(),
                        task.getMediaVersion(),
                        objectKey,
                        urlSnapshot,
                        derivativeImage.width(),
                        derivativeImage.height(),
                        derivativeImage.contentType(),
                        derivativeImage.bytes().length
                ));

        derivative.replaceWith(
                objectKey,
                urlSnapshot,
                derivativeImage.width(),
                derivativeImage.height(),
                derivativeImage.contentType(),
                derivativeImage.bytes().length
        );
        mediaDerivativeRepository.save(derivative);

        if (mediaFileRecord.getStatus() == MediaFileStatus.CONFIRMED) {
            mediaFileRecord.markReady();
        }
    }

    private BufferedImage decodeImage(byte[] bytes) {
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
            BufferedImage sourceImage = ImageIO.read(input);
            if (sourceImage == null) {
                throw new NonRetriableMediaProcessingException("unsupported or corrupted source image");
            }
            return sourceImage;
        } catch (IOException e) {
            throw new NonRetriableMediaProcessingException("failed to decode image", e);
        }
    }

    private DerivativeImageResult toThumbnailImage(BufferedImage sourceImage) {
        int sourceWidth = sourceImage.getWidth();
        int sourceHeight = sourceImage.getHeight();
        int[] targetSize = calculateTargetSize(
                sourceWidth,
                sourceHeight,
                derivativeProperties.getThumbnailMaxWidth(),
                derivativeProperties.getThumbnailMaxHeight()
        );

        BufferedImage resized = new BufferedImage(targetSize[0], targetSize[1], BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = resized.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(sourceImage, 0, 0, targetSize[0], targetSize[1], null);
        } finally {
            graphics.dispose();
        }

        return encodeImage(resized);
    }

    private int[] calculateTargetSize(int sourceWidth, int sourceHeight, int maxWidth, int maxHeight) {
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            throw new NonRetriableMediaProcessingException("invalid source image size");
        }
        double widthRatio = (double) Math.max(1, maxWidth) / sourceWidth;
        double heightRatio = (double) Math.max(1, maxHeight) / sourceHeight;
        double ratio = Math.min(1.0d, Math.min(widthRatio, heightRatio));
        int targetWidth = Math.max(1, (int) Math.round(sourceWidth * ratio));
        int targetHeight = Math.max(1, (int) Math.round(sourceHeight * ratio));
        return new int[]{targetWidth, targetHeight};
    }

    private DerivativeImageResult encodeImage(BufferedImage image) {
        int quality = toWebpQuality(derivativeProperties.getWebpQuality());
        try {
            byte[] encoded = ImmutableImage.fromAwt(image)
                    .forWriter(WebpWriter.DEFAULT.withQ(quality))
                    .bytes();
            return new DerivativeImageResult(
                    encoded,
                    "image/webp",
                    "webp",
                    image.getWidth(),
                    image.getHeight()
            );
        } catch (IOException | RuntimeException e) {
            throw new NonRetriableMediaProcessingException("failed to encode webp image", e);
        }
    }

    private int toWebpQuality(float value) {
        float clamped = Math.max(0.1f, Math.min(1.0f, value));
        return Math.round(clamped * 100.0f);
    }

    private String buildDerivativeObjectKey(String sourceObjectKey, MediaDerivativeTask task, String extension) {
        String normalized = sourceObjectKey == null ? "" : sourceObjectKey.trim();
        String baseName = normalized;
        int extensionIndex = baseName.lastIndexOf('.');
        if (extensionIndex > 0) {
            baseName = baseName.substring(0, extensionIndex);
        }

        String derivedPrefix;
        if (normalized.contains("/raw/")) {
            derivedPrefix = normalized.replace("/raw/", "/derived/");
            if (extensionIndex > 0) {
                derivedPrefix = derivedPrefix.substring(0, derivedPrefix.lastIndexOf('.'));
            }
        } else {
            LocalDate now = LocalDate.now();
            derivedPrefix = trimSlash(mediaWorkerS3Properties.getKeyPrefix()) + "/derived/"
                    + now.getYear() + "/" + now.getMonthValue() + "/" + now.getDayOfMonth()
                    + "/" + sanitize(task.getMediaId() + "_" + baseName);
        }

        String profileSegment = task.getDerivativeProfile().name().toLowerCase(Locale.ROOT);
        return derivedPrefix
                + "_"
                + profileSegment
                + "_v"
                + task.getMediaVersion()
                + "."
                + extension;
    }

    private void uploadDerivative(String bucketName, String objectKey, DerivativeImageResult derivativeImage) {
        mediaWorkerS3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(objectKey)
                        .contentType(derivativeImage.contentType())
                        .contentLength((long) derivativeImage.bytes().length)
                        .build(),
                RequestBody.fromBytes(derivativeImage.bytes())
        );
    }

    private String buildBaseUrl(String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            return null;
        }
        if (StringUtils.hasText(mediaWorkerS3Properties.getCloudfrontDomain())) {
            String domain = mediaWorkerS3Properties.getCloudfrontDomain().trim();
            if (domain.endsWith("/")) {
                domain = domain.substring(0, domain.length() - 1);
            }
            return domain + "/" + objectKey;
        }
        return "https://" + mediaWorkerS3Properties.getBucket().trim()
                + ".s3."
                + mediaWorkerS3Properties.getRegion().trim()
                + ".amazonaws.com/"
                + objectKey;
    }

    private String trimSlash(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String result = value.trim();
        while (result.startsWith("/")) {
            result = result.substring(1);
        }
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private String sanitize(String value) {
        if (!StringUtils.hasText(value)) {
            return "media";
        }
        return value.replaceAll("[^a-zA-Z0-9/_-]", "_");
    }
}
