package com.example.csvbatch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppPropertiesTest {
    @ParameterizedTest
    @ValueSource(strings = {"customer_id", "CUSTOMER_ID", "Customer_Id"})
    void rejectsLogicalPropertiesTargetingTheSameUnquotedColumn(String alias) {
        assertThatThrownBy(() -> config(Map.of(
                "customerId", new AppProperties.Property("customer_id", 64),
                "departmentId", new AppProperties.Property(alias, 32))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Each property must map to a different physical column");
    }

    @Test
    void acceptsDistinctColumnsWithoutChangingConfiguredNames() {
        var properties = Map.of(
                "customerId", new AppProperties.Property("CUSTOMER_ID", 64),
                "departmentId", new AppProperties.Property("Department_Id", 32));

        assertThat(config(properties).properties()).isEqualTo(properties);
    }

    private static AppProperties config(Map<String, AppProperties.Property> properties) {
        return new AppProperties(false, Path.of("target"), 2, 128, 8192, 65536,
                100, 16, 10000, 30, 3, "business_record", properties);
    }
}
