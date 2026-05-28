package com.nevinsight.scheduler.handler;

import com.nevinsight.collector.service.SocialCrawlerService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * 社交平台爬虫定时任务。
 *
 * 推荐 CRON: 0 0 2 * * ?  （每日凌晨 02:00）
 *
 * 配置中支持的可选品牌-关键词；这里硬编码默认 3 个品牌；
 * 真实部署可改读 BrandConfigProperties.searchKeywords。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrawlerJobHandler {

    private final SocialCrawlerService crawlerService;

    private static final List<String> ALL_PLATFORMS = Arrays.asList("xhs", "dy", "ks", "bili", "wb");

    /**
     * 每日定时触发：所有启用品牌 × 所有平台
     * 关键词从 brand_keyword_config 表加载（enabled=1）
     * XXL-JOB CRON: 0 0 2 * * ?
     */
    @XxlJob("socialCrawlerHandler")
    public void runDaily() {
        log.info("[XXL-JOB] 开始每日社交爬取...");
        List<String> brands = crawlerService.listEnabledBrands();
        if (brands.isEmpty()) {
            log.warn("[XXL-JOB] brand_keyword_config 表无启用品牌，跳过");
            return;
        }
        for (String brand : brands) {
            try {
                // keywords=null → SocialCrawlerService 自动从 DB 加载
                crawlerService.trigger(brand, ALL_PLATFORMS, null, 20, true, "cookie");
                log.info("[XXL-JOB] 已触发品牌 {} × {} 个平台", brand, ALL_PLATFORMS.size());
            } catch (Exception ex) {
                log.error("[XXL-JOB] 触发失败品牌={} err={}", brand, ex.getMessage());
            }
        }
        log.info("[XXL-JOB] 每日社交爬取触发完成 ({} 品牌)", brands.size());
    }

    /**
     * 单品牌单平台任务（XXL-JOB 参数：brand=猛士&platform=xhs&keywords=猛士917,猛士M9）
     */
    @XxlJob("socialCrawlerSingleHandler")
    public void runSingle() {
        String param = com.xxl.job.core.context.XxlJobHelper.getJobParam();
        log.info("[XXL-JOB] 单平台爬取参数: {}", param);
        if (param == null || param.isEmpty()) {
            log.warn("[XXL-JOB] 缺少参数");
            return;
        }
        Map<String, String> kv = parseKv(param);
        String brand = kv.getOrDefault("brand", "");
        String platform = kv.getOrDefault("platform", "");
        String kw = kv.getOrDefault("keywords", "");
        if (brand.isEmpty() || platform.isEmpty() || kw.isEmpty()) {
            log.warn("[XXL-JOB] 参数不完整: brand={} platform={} keywords={}", brand, platform, kw);
            return;
        }
        crawlerService.trigger(
                brand,
                Collections.singletonList(platform),
                Arrays.asList(kw.split(",")),
                20, true, "cookie");
    }

    private Map<String, String> parseKv(String s) {
        java.util.Map<String, String> m = new java.util.HashMap<>();
        for (String part : s.split("&")) {
            int idx = part.indexOf('=');
            if (idx > 0) m.put(part.substring(0, idx).trim(), part.substring(idx + 1).trim());
        }
        return m;
    }
}
