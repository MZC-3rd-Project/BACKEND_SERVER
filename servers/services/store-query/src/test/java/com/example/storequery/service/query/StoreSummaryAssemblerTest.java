package com.example.storequery.service.query;

import com.example.storequery.entity.StoreQueryStatus;
import com.example.storequery.repository.StoreReadModelSearchRow;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class StoreSummaryAssemblerTest {

    private final StoreQueryMediaUrlNormalizer mediaUrlNormalizer =
        new StoreQueryMediaUrlNormalizer("https://d179i4pv5hzdkg.cloudfront.net");
    private final StoreSummaryAssembler storeSummaryAssembler =
        new StoreSummaryAssembler(new StoreThumbnailResolver(mediaUrlNormalizer));

    @Test
    void toListResponse_mapsSearchRowSourceWithTypedStatusAndThumbnail() {
        StoreReadModelSearchRow row = new StoreReadModelSearchRow() {
            @Override
            public Long getStoreId() {
                return 1L;
            }

            @Override
            public Long getUserId() {
                return 100L;
            }

            @Override
            public String getStoreName() {
                return "MZC Store";
            }

            @Override
            public String getStatus() {
                return " active ";
            }

            @Override
            public String getDescription() {
                return "desc";
            }

            @Override
            public String getPrimaryContactValue() {
                return "010-1234-5678";
            }

            @Override
            public String getDefaultAddress() {
                return "Seoul";
            }

            @Override
            public String getOwnerNickname() {
                return "owner";
            }

            @Override
            public Long getThumbnailMediaId() {
                return 10L;
            }

            @Override
            public String getThumbnailUrl() {
                return "https://thumb";
            }

            @Override
            public Integer getThumbnailSortOrder() {
                return 0;
            }

            @Override
            public LocalDateTime getSourceUpdatedAt() {
                return LocalDateTime.now();
            }

            @Override
            public Double getSortRank() {
                return 0.9d;
            }
        };

        var response = storeSummaryAssembler.toListResponse(new StoreSearchRowSummarySource(row));

        assertThat(response.storeId()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo(StoreQueryStatus.ACTIVE);
        assertThat(response.thumbnail()).isNotNull();
        assertThat(response.thumbnail().mediaId()).isEqualTo(10L);
    }
}
