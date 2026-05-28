package com.nevinsight.scheduler.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class XxlJobConfig {

    @Value("${xxl.job.admin.addresses:}")
    private String adminAddresses;

    @Value("${xxl.job.executor.appname:nev-insight}")
    private String appName;

    @Value("${xxl.job.executor.port:9999}")
    private int port;

    @Value("${xxl.job.accessToken:}")
    private String accessToken;

    @Value("${xxl.job.enabled:false}")
    private boolean enabled;

    @Bean
    public XxlJobSpringExecutor xxlJobExecutor() {
        if (!enabled || adminAddresses == null || adminAddresses.isEmpty()) {
            log.info("[XXL-JOB] 未配置或已禁用，跳过 Executor 初始化");
            return null;
        }
        XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
        executor.setAdminAddresses(adminAddresses);
        executor.setAppname(appName);
        executor.setPort(port);
        executor.setAccessToken(accessToken);
        log.info("[XXL-JOB] Executor 初始化: appname={}, port={}", appName, port);
        return executor;
    }
}
