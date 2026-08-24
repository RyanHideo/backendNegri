package com.sinapse.ccm.vsi;

import com.sinapse.ccm.modbus.TagValue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

@Service
public class VsiPowerService {

    public static final String POWER_1_TAG = "Potencia1Vsi";
    public static final String POWER_2_TAG = "Potencia2Vsi";
    public static final String MAIN_TRANSFORMER_POWER_TAG = "KW";
    public static final String POWER_1_COMPARISON_TAG = "Potencia1VsiComparativoKva";
    public static final String POWER_2_COMPARISON_TAG = "Potencia2VsiComparativoKva";
    public static final String VSI_TRANSFORMER_POWER_TAG = "PotenciaTrafoVsiKva";
    public static final String GENERAL_POWER_TAG = "PotenciaGeralKva";
    public static final String MAX_POWER_KW_TAG = "PotenciaMaximaVsiKw";
    public static final String MAX_POWER_COMPARISON_TAG = "PotenciaMaximaVsiComparativoKva";

    private final double kwToKvaFactor;
    private final double kvaToKwFactor;
    private final double maximumPowerKw;

    public VsiPowerService(
            @Value("${vsi.power.kw-to-kva-factor:1.25}") double kwToKvaFactor,
            @Value("${vsi.power.kva-to-kw-factor:0.8}") double kvaToKwFactor,
            @Value("${vsi.power.maximum-kw:150}") double maximumPowerKw
    ) {
        if (kwToKvaFactor <= 0 || kvaToKwFactor <= 0 || maximumPowerKw <= 0) {
            throw new IllegalArgumentException("Os fatores e a potência máxima do VSI devem ser maiores que zero");
        }

        this.kwToKvaFactor = kwToKvaFactor;
        this.kvaToKwFactor = kvaToKwFactor;
        this.maximumPowerKw = maximumPowerKw;
    }

    public void addKvaComparison(Map<String, TagValue> tags) {
        TagValue power1 = tags.get(POWER_1_TAG);
        TagValue power2 = tags.get(POWER_2_TAG);

        if (power1 == null && power2 == null) {
            return;
        }

        TagValue power1Comparison = addComparison(tags, POWER_1_COMPARISON_TAG, power1);
        TagValue power2Comparison = addComparison(tags, POWER_2_COMPARISON_TAG, power2);

        TagValue latestReading = Stream.of(power1, power2)
                .filter(Objects::nonNull)
                .max(Comparator.comparing(TagValue::getTs))
                .orElseThrow();

        tags.put(MAX_POWER_KW_TAG, new TagValue(
                MAX_POWER_KW_TAG,
                maximumPowerKw,
                latestReading.getTs(),
                TagValue.Quality.GOOD,
                null
        ));
        tags.put(MAX_POWER_COMPARISON_TAG, new TagValue(
                MAX_POWER_COMPARISON_TAG,
                kwToKva(maximumPowerKw),
                latestReading.getTs(),
                TagValue.Quality.GOOD,
                null
        ));

        if (power1Comparison != null && power2Comparison != null) {
            TagValue vsiTransformerPower = sumTags(
                    VSI_TRANSFORMER_POWER_TAG,
                    power1Comparison,
                    power2Comparison
            );
            tags.put(VSI_TRANSFORMER_POWER_TAG, vsiTransformerPower);

            TagValue mainTransformerPower = tags.get(MAIN_TRANSFORMER_POWER_TAG);
            if (mainTransformerPower != null) {
                tags.put(
                        GENERAL_POWER_TAG,
                        sumTags(GENERAL_POWER_TAG, mainTransformerPower, vsiTransformerPower)
                );
            }
        }
    }

    public double kwToKva(double powerKw) {
        return roundToTwo(powerKw * kwToKvaFactor);
    }

    public double kvaToKw(double powerKva) {
        return roundToTwo(powerKva * kvaToKwFactor);
    }

    public boolean isCalculatedTag(String tagName) {
        return POWER_1_COMPARISON_TAG.equalsIgnoreCase(tagName)
                || POWER_2_COMPARISON_TAG.equalsIgnoreCase(tagName)
                || VSI_TRANSFORMER_POWER_TAG.equalsIgnoreCase(tagName)
                || GENERAL_POWER_TAG.equalsIgnoreCase(tagName)
                || MAX_POWER_KW_TAG.equalsIgnoreCase(tagName)
                || MAX_POWER_COMPARISON_TAG.equalsIgnoreCase(tagName);
    }

    private TagValue addComparison(Map<String, TagValue> tags, String tagName, TagValue source) {
        if (source == null) {
            return null;
        }

        TagValue comparison = new TagValue(
                tagName,
                kwToKva(source.getValue()),
                source.getTs(),
                source.getQuality(),
                source.getError()
        );
        tags.put(tagName, comparison);
        return comparison;
    }

    private TagValue sumTags(String tagName, TagValue first, TagValue second) {
        TagValue.Quality quality = first.getQuality() == TagValue.Quality.GOOD
                && second.getQuality() == TagValue.Quality.GOOD
                ? TagValue.Quality.GOOD
                : TagValue.Quality.BAD;
        String error = first.getError() != null ? first.getError() : second.getError();

        return new TagValue(
                tagName,
                roundToTwo(first.getValue() + second.getValue()),
                first.getTs().isAfter(second.getTs()) ? first.getTs() : second.getTs(),
                quality,
                error
        );
    }

    private static double roundToTwo(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
