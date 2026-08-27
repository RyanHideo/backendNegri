package com.sinapse.ccm.vsi;

import com.sinapse.ccm.modbus.TagValue;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VsiPowerServiceTests {

    private final VsiPowerService service = new VsiPowerService(1.25, 0.8, 150, 1000);

    @Test
    void convertsBetweenKwAndKvaUsingConfiguredFactors() {
        assertThat(service.kwToKva(100)).isEqualTo(125);
        assertThat(service.kvaToKw(125)).isEqualTo(100);
        assertThat(service.kwToKva(150)).isEqualTo(187.5);
        assertThat(service.rawToPercentage(999)).isEqualTo(99.9);
        assertThat(service.rawToKw(999)).isEqualTo(149.85);
        assertThat(service.rawToKw(1000)).isEqualTo(150);
    }

    @Test
    void convertsRawVsiPercentageToKwAndPublishesTheComparisonInKva() {
        Instant timestamp = Instant.parse("2026-08-23T12:00:00Z");
        Map<String, TagValue> tags = new HashMap<>();
        tags.put(VsiPowerService.MAIN_TRANSFORMER_POWER_TAG, tag(VsiPowerService.MAIN_TRANSFORMER_POWER_TAG, 200, timestamp, TagValue.Quality.GOOD));
        tags.put(VsiPowerService.POWER_1_TAG, tag(VsiPowerService.POWER_1_TAG, 800, timestamp, TagValue.Quality.GOOD));
        tags.put(VsiPowerService.POWER_2_TAG, tag(VsiPowerService.POWER_2_TAG, 999, timestamp, TagValue.Quality.BAD));

        service.addKvaComparison(tags);

        assertThat(tags.get(VsiPowerService.POWER_1_TAG).getValue()).isEqualTo(120);
        assertThat(tags.get(VsiPowerService.POWER_1_TAG).getQuality()).isEqualTo(TagValue.Quality.GOOD);
        assertThat(tags.get(VsiPowerService.POWER_2_TAG).getValue()).isEqualTo(149.85);
        assertThat(tags.get(VsiPowerService.POWER_2_TAG).getQuality()).isEqualTo(TagValue.Quality.BAD);
        assertThat(tags.get(VsiPowerService.POWER_1_COMPARISON_TAG).getValue()).isEqualTo(150);
        assertThat(tags.get(VsiPowerService.POWER_1_COMPARISON_TAG).getQuality()).isEqualTo(TagValue.Quality.GOOD);
        assertThat(tags.get(VsiPowerService.POWER_2_COMPARISON_TAG).getValue()).isEqualTo(187.31);
        assertThat(tags.get(VsiPowerService.POWER_2_COMPARISON_TAG).getQuality()).isEqualTo(TagValue.Quality.BAD);
        assertThat(tags.get(VsiPowerService.VSI_TRANSFORMER_POWER_TAG).getValue()).isEqualTo(337.31);
        assertThat(tags.get(VsiPowerService.VSI_TRANSFORMER_POWER_TAG).getQuality()).isEqualTo(TagValue.Quality.BAD);
        assertThat(tags.get(VsiPowerService.GENERAL_POWER_TAG).getValue()).isEqualTo(537.31);
        assertThat(tags.get(VsiPowerService.GENERAL_POWER_TAG).getQuality()).isEqualTo(TagValue.Quality.BAD);
        assertThat(tags.get(VsiPowerService.MAX_POWER_KW_TAG).getValue()).isEqualTo(150);
        assertThat(tags.get(VsiPowerService.MAX_POWER_COMPARISON_TAG).getValue()).isEqualTo(187.5);
    }

    private static TagValue tag(String name, double value, Instant timestamp, TagValue.Quality quality) {
        return new TagValue(name, value, timestamp, quality, null);
    }
}
