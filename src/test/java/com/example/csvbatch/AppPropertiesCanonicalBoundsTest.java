package com.example.csvbatch;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppPropertiesCanonicalBoundsTest {
    @Test
    void acceptsLargestMaxOldIdsWithSafeCanonicalBounds() {
        var properties = config(1_394_469, 8192, 65536);

        assertThat(properties.maxOldIds()).isEqualTo(1_394_469);
        assertThat((long) 1540 * properties.maxOldIds() + 2).isEqualTo(2_147_482_262L);
        assertThat((long) 1540 * properties.maxOldIds() + 1024).isEqualTo(2_147_483_284L);
    }

    @Test
    void rejectsMaxOldIdsWhoseCanonicalRecordBoundOverflows() {
        assertThatThrownBy(() -> config(1_394_470, 8192, 65536))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Canonical processing limits overflow an int");
    }

    @Test
    void acceptsConfiguredLimitsLargerThanDerivedCanonicalBounds() {
        var properties = config(100, 2_147_000_000, 2_147_000_000);

        assertThat(properties.maxFieldChars()).isEqualTo(2_147_000_000);
        assertThat(properties.maxRecordChars()).isEqualTo(2_147_000_000);
    }

    private static AppProperties config(int maxOldIds, int maxFieldChars, int maxRecordChars) {
        return new AppProperties(false, Path.of("target"), 2, 128, maxFieldChars, maxRecordChars,
                maxOldIds, 16, 10000, 30, 3, "business_record",
                Map.of("customerId", new AppProperties.Property("customer_id", 64)));
    }
}
