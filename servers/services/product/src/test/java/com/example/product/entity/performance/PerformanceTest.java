package com.example.product.entity.performance;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class PerformanceTest {

    @Test
    void create_withLegacyFactory_keepsExtendedMetadataNull() {
        Performance performance = Performance.create(
                1L,
                "Olympic Hall",
                LocalDate.of(2030, 5, 1),
                LocalTime.of(19, 0),
                500
        );

        assertThat(performance.getRunningTimeMinutes()).isNull();
        assertThat(performance.getAgeLimit()).isNull();
        assertThat(performance.getVenueAddress()).isNull();
        assertThat(performance.getBookingNotice()).isNull();
        assertThat(performance.getOrganizer()).isNull();
        assertThat(performance.getHost()).isNull();
    }

    @Test
    void create_withExtendedMetadata_setsAllDetailFields() {
        Performance performance = Performance.create(
                1L,
                "Olympic Hall",
                LocalDate.of(2030, 5, 1),
                LocalTime.of(19, 0),
                500,
                130,
                "15+",
                "Seoul Songpa-gu",
                "Late entry may be restricted.",
                "DonMoa Live",
                "DonMoa"
        );

        assertThat(performance.getRunningTimeMinutes()).isEqualTo(130);
        assertThat(performance.getAgeLimit()).isEqualTo("15+");
        assertThat(performance.getVenueAddress()).isEqualTo("Seoul Songpa-gu");
        assertThat(performance.getBookingNotice()).isEqualTo("Late entry may be restricted.");
        assertThat(performance.getOrganizer()).isEqualTo("DonMoa Live");
        assertThat(performance.getHost()).isEqualTo("DonMoa");
    }

    @Test
    void update_withExtendedMetadata_replacesAllDetailFields() {
        Performance performance = Performance.create(
                1L,
                "Olympic Hall",
                LocalDate.of(2030, 5, 1),
                LocalTime.of(19, 0),
                500
        );

        performance.update(
                "Jamsil Arena",
                LocalDate.of(2030, 6, 1),
                LocalTime.of(20, 0),
                700,
                150,
                "12+",
                "Seoul Jamsil",
                "Doors open 1 hour before the show.",
                "Moa Entertainment",
                "DonMoa Host"
        );

        assertThat(performance.getVenue()).isEqualTo("Jamsil Arena");
        assertThat(performance.getPerformanceDate()).isEqualTo(LocalDate.of(2030, 6, 1));
        assertThat(performance.getPerformanceTime()).isEqualTo(LocalTime.of(20, 0));
        assertThat(performance.getTotalSeats()).isEqualTo(700);
        assertThat(performance.getRunningTimeMinutes()).isEqualTo(150);
        assertThat(performance.getAgeLimit()).isEqualTo("12+");
        assertThat(performance.getVenueAddress()).isEqualTo("Seoul Jamsil");
        assertThat(performance.getBookingNotice()).isEqualTo("Doors open 1 hour before the show.");
        assertThat(performance.getOrganizer()).isEqualTo("Moa Entertainment");
        assertThat(performance.getHost()).isEqualTo("DonMoa Host");
    }

    @Test
    void update_withLegacySignature_preservesExtendedMetadata() {
        Performance performance = Performance.create(
                1L,
                "Olympic Hall",
                LocalDate.of(2030, 5, 1),
                LocalTime.of(19, 0),
                500,
                130,
                "15+",
                "Seoul Songpa-gu",
                "Late entry may be restricted.",
                "DonMoa Live",
                "DonMoa"
        );

        performance.update(
                "Jamsil Arena",
                LocalDate.of(2030, 6, 1),
                LocalTime.of(20, 0),
                700
        );

        assertThat(performance.getRunningTimeMinutes()).isEqualTo(130);
        assertThat(performance.getAgeLimit()).isEqualTo("15+");
        assertThat(performance.getVenueAddress()).isEqualTo("Seoul Songpa-gu");
        assertThat(performance.getBookingNotice()).isEqualTo("Late entry may be restricted.");
        assertThat(performance.getOrganizer()).isEqualTo("DonMoa Live");
        assertThat(performance.getHost()).isEqualTo("DonMoa");
    }
}
