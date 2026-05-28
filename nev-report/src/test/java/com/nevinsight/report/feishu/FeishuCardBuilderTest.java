package com.nevinsight.report.feishu;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nevinsight.intelligence.service.MetricsCalculator;
import com.nevinsight.model.dto.response.CardTextSections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class FeishuCardBuilderTest {

    private FeishuCardBuilder builder;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        builder = new FeishuCardBuilder(objectMapper);
    }

    private MetricsCalculator.DailyKPI sampleKpi() {
        return MetricsCalculator.DailyKPI.builder()
                .totalMentions(44).positiveRatio(0.341).negativeRatio(0.364)
                .avgSentimentScore(3.0).statusLevel("red")
                .veryPositiveCount(1).positiveCount(14).neutralCount(13)
                .negativeCount(15).veryNegativeCount(1)
                .build();
    }

    // ===== Task 1: JSON 2.0 骨架 =====

    @Test
    void card_has_schema_v2() throws Exception {
        String json = builder.build("猛士", LocalDate.of(2026, 5, 7), sampleKpi(), null, List.of(), null);
        Map<?, ?> card = card(json);
        assertThat(card.get("schema")).isEqualTo("2.0");
    }

    @Test
    void card_config_uses_width_mode_fill() throws Exception {
        String json = builder.build("猛士", LocalDate.of(2026, 5, 7), sampleKpi(), null, List.of(), null);
        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) card(json).get("config");
        assertThat(config.get("width_mode")).isEqualTo("fill");
        assertThat(config).doesNotContainKey("wide_screen_mode");
    }

    @Test
    void elements_are_under_body_not_card() throws Exception {
        String json = builder.build("猛士", LocalDate.of(2026, 5, 7), sampleKpi(), null, List.of(), null);
        @SuppressWarnings("unchecked")
        Map<String, Object> card = (Map<String, Object>) objectMapper.readValue(json, Map.class).get("card");
        assertThat(card).doesNotContainKey("elements");
        Map<?, ?> body = (Map<?, ?>) card.get("body");
        assertThat(body).isNotNull();
        assertThat(body.get("elements")).isInstanceOf(List.class);
    }

    @Test
    void no_note_or_action_tags_in_output() throws Exception {
        String json = builder.build("猛士", LocalDate.of(2026, 5, 7), sampleKpi(), null, List.of(), "http://example.com");
        assertThat(json).doesNotContain("\"note\"");
        assertThat(json).doesNotContain("\"action\"");
    }

    // ===== Task 2: KPI interactive_container =====

    @Test
    void kpi_columns_use_interactive_container() throws Exception {
        String json = builder.build("猛士", LocalDate.of(2026, 5, 7), sampleKpi(), null, List.of(), null);
        assertThat(json).contains("\"interactive_container\"");
        assertThat(json).contains("\"background_style\"");
    }

    // ===== Task 3: VChart 趋势图 =====

    @Test
    void trend_chart_present_when_data_provided() throws Exception {
        List<FeishuCardBuilder.TrendPoint> trend = List.of(
                new FeishuCardBuilder.TrendPoint("05-04", 50, 3.2),
                new FeishuCardBuilder.TrendPoint("05-07", 44, 3.0)
        );
        String json = builder.build("猛士", LocalDate.of(2026, 5, 7), sampleKpi(), null, trend, null);
        List<?> elements = bodyElements(json);
        boolean hasChart = elements.stream()
                .filter(e -> e instanceof Map)
                .anyMatch(e -> "chart".equals(((Map<?, ?>) e).get("tag")));
        assertThat(hasChart).isTrue();
    }

    @Test
    void trend_chart_absent_when_empty_trend() throws Exception {
        String json = builder.build("猛士", LocalDate.of(2026, 5, 7), sampleKpi(), null, List.of(), null);
        assertThat(json).doesNotContain("\"chart\"");
    }

    @Test
    void trend_chart_absent_when_null_trend() throws Exception {
        String json = builder.build("猛士", LocalDate.of(2026, 5, 7), sampleKpi(), null, null, null);
        assertThat(json).doesNotContain("\"chart\"");
    }

    // ===== helpers =====

    @SuppressWarnings("unchecked")
    private Map<?, ?> card(String json) throws Exception {
        return (Map<?, ?>) objectMapper.readValue(json, Map.class).get("card");
    }

    @SuppressWarnings("unchecked")
    private List<?> bodyElements(String json) throws Exception {
        Map<?, ?> body = (Map<?, ?>) card(json).get("body");
        return (List<?>) body.get("elements");
    }
}
