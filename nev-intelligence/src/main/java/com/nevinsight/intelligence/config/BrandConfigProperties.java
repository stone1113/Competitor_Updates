package com.nevinsight.intelligence.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "nevinsight.brand")
public class BrandConfigProperties {

    private String name = "猛士汽车";
    private String dbName = "猛士";
    private Map<String, String> competitorBrands = new HashMap<>();
    private Map<String, ModelBenchmarkRule> modelBenchmarkRules = new HashMap<>();
    private Map<String, List<String>> searchKeywords = new HashMap<>();
    private double redNegativeRatio = 0.25;
    private double yellowNegativeRatio = 0.15;

    @javax.annotation.PostConstruct
    public void initDefaults() {
        if (searchKeywords.isEmpty()) {
            searchKeywords.put("猛士", Arrays.asList("猛士汽车", "猛士M817", "猛士917"));
            searchKeywords.put("坦克", Arrays.asList("坦克汽车", "坦克500", "坦克700"));
            searchKeywords.put("方程豹", Arrays.asList("方程豹汽车", "豹8", "豹5"));
            searchKeywords.put("问界", Arrays.asList("问界M7", "问界M9"));
        }
        if (competitorBrands.isEmpty()) {
            competitorBrands.put("坦克", "坦克");
            competitorBrands.put("方程豹", "方程豹");
            competitorBrands.put("问界", "问界");
        }
    }

    @Data
    public static class ModelBenchmarkRule {
        private List<String> core;
        private List<String> opportunity;
    }
}
