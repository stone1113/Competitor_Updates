package com.nevinsight.collector.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nevinsight.collector.client.CrawlerServiceClient;
import com.nevinsight.model.entity.core.OfficialAccountConfig;
import com.nevinsight.model.mapper.core.OfficialAccountConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 触发竞品官方账号抓取（MediaCrawler creator 模式）。
 *
 * 流程：
 *   1. 读 official_account_config (is_enabled=1) 按 platform 分组
 *   2. 每平台 → 调 crawler-service /crawl mode=creator + creator_ids
 *   3. crawler-service 子进程把帖落进 weibo_note / douyin_aweme / xhs_note
 *   4. 抓取完成后由 OfficialPostIngester 把官号帖 ingest 到 web_search_news
 *      （触发由 OfficialAccountScheduler 或 controller 在 crawl 后调用）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfficialAccountCrawlerService {

    /** crawler-service 平台代码映射（DB platform = wb/dy/xhs；crawler-service 接受 wb/dy/xhs） */
    private static final Set<String> SUPPORTED_PLATFORMS = Set.of("wb", "dy", "xhs");

    private final OfficialAccountConfigMapper configMapper;
    private final CrawlerServiceClient crawlerClient;

    public CrawlResult crawlAll() {
        return crawlByPlatform(null);
    }

    /** platform=null 跑全部启用平台；指定时仅跑该平台。 */
    public CrawlResult crawlByPlatform(String platform) {
        LambdaQueryWrapper<OfficialAccountConfig> qw = new LambdaQueryWrapper<>();
        qw.eq(OfficialAccountConfig::getIsEnabled, true);
        if (platform != null && !platform.isEmpty()) {
            qw.eq(OfficialAccountConfig::getPlatform, platform);
        }
        List<OfficialAccountConfig> configs = configMapper.selectList(qw);
        if (configs.isEmpty()) {
            log.warn("[OfficialAccount] no enabled accounts for platform={}", platform);
            return new CrawlResult(0, 0, Collections.emptyList());
        }

        // 按 platform 聚合，一个 platform 一次任务（creator_id 多账号支持逗号分隔）
        Map<String, List<OfficialAccountConfig>> byPlatform = configs.stream()
                .filter(c -> SUPPORTED_PLATFORMS.contains(c.getPlatform()))
                .collect(Collectors.groupingBy(
                        OfficialAccountConfig::getPlatform,
                        LinkedHashMap::new,
                        Collectors.toList()));

        int tasksFired = 0;
        int accountsCovered = 0;
        List<String> taskIds = new ArrayList<>();
        long now = System.currentTimeMillis();

        for (Map.Entry<String, List<OfficialAccountConfig>> e : byPlatform.entrySet()) {
            String plat = e.getKey();
            List<OfficialAccountConfig> accounts = e.getValue();

            // One creator id can fail the whole MediaCrawler creator task. Fire one task
            // per official account so a bad id/cookie issue does not block the rest.
            for (OfficialAccountConfig account : accounts) {
                String creatorId = account.getAccountId();
                if (creatorId == null || creatorId.isEmpty() || creatorId.startsWith("TODO")) {
                    log.warn("[OfficialAccount] skip invalid account id platform={} brand={} account={}",
                            plat, account.getBrandName(), creatorId);
                    continue;
                }

                try {
                    String taskId = crawlerClient.triggerCreatorCrawl(
                            plat, account.getBrandName(), Collections.singletonList(creatorId), 20, "cookie");
                taskIds.add(taskId);
                tasksFired++;
                    accountsCovered++;
                    log.info("[OfficialAccount] fired platform={} brand={} account={} task_id={}",
                            plat, account.getBrandName(), creatorId, taskId);

                    // 更新 last_crawl_ts
                    account.setLastCrawlTs(now);
                    configMapper.updateById(account);
                } catch (Exception ex) {
                    log.error("[OfficialAccount] platform={} brand={} account={} failed: {}",
                            plat, account.getBrandName(), creatorId, ex.getMessage());
                }
            }
        }

        return new CrawlResult(tasksFired, accountsCovered, taskIds);
    }

    public static class CrawlResult {
        public final int tasksFired;
        public final int accountsCovered;
        public final List<String> taskIds;

        public CrawlResult(int tasksFired, int accountsCovered, List<String> taskIds) {
            this.tasksFired = tasksFired;
            this.accountsCovered = accountsCovered;
            this.taskIds = taskIds;
        }
    }
}
