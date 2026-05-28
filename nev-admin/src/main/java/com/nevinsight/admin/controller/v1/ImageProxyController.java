package com.nevinsight.admin.controller.v1;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Set;

/**
 * 图片代理：跨域 / 防盗链图片转发。
 *
 * 主要解决浏览器 ORB（Opaque Response Blocking）拦截官号微博图片（sinaimg.cn 等）。
 * 后端拉图带正确 Referer，前端 img 改用代理 URL，浏览器视为同源。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/proxy")
public class ImageProxyController {

    /** 白名单 host 后缀（覆盖子域）。 */
    private static final Set<String> ALLOWED_HOST_SUFFIXES = Set.of(
            "sinaimg.cn",              // 微博图片
            "qpic.cn",                 // 腾讯（微信公众号）
            "douyinpic.com",           // 抖音
            "byteimg.com",             // 字节系
            "xhscdn.com",              // 小红书
            "bdstatic.com"             // 百度
    );

    private final RestTemplate http;

    public ImageProxyController() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(15000);
        this.http = new RestTemplate(factory);
    }

    @GetMapping("/image")
    public ResponseEntity<byte[]> proxyImage(@RequestParam String url) {
        // 1. 解析 + 校验 URL
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (host == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            return ResponseEntity.badRequest().build();
        }

        // 2. 白名单（按 host 后缀匹配子域）
        String lowerHost = host.toLowerCase();
        boolean allowed = ALLOWED_HOST_SUFFIXES.stream().anyMatch(lowerHost::endsWith);
        if (!allowed) {
            log.warn("[ImageProxy] blocked host: {}", host);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // 3. 转发，按域定制 Referer 绕防盗链
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent",
                    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) " +
                    "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1");
            if (lowerHost.endsWith("sinaimg.cn")) {
                headers.set("Referer", "https://m.weibo.cn/");
            } else if (lowerHost.endsWith("douyinpic.com") || lowerHost.endsWith("byteimg.com")) {
                headers.set("Referer", "https://www.douyin.com/");
            } else if (lowerHost.endsWith("xhscdn.com")) {
                headers.set("Referer", "https://www.xiaohongshu.com/");
            } else if (lowerHost.endsWith("qpic.cn")) {
                headers.set("Referer", "https://mp.weixin.qq.com/");
            }
            headers.setAccept(java.util.List.of(MediaType.IMAGE_JPEG, MediaType.IMAGE_PNG,
                    MediaType.IMAGE_GIF, MediaType.ALL));

            ResponseEntity<byte[]> resp = http.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);

            HttpHeaders out = new HttpHeaders();
            MediaType ct = resp.getHeaders().getContentType();
            out.setContentType(ct != null ? ct : MediaType.IMAGE_JPEG);
            out.setCacheControl("public, max-age=3600");
            // 允许浏览器跨域使用
            out.set("Cross-Origin-Resource-Policy", "cross-origin");
            return ResponseEntity.status(resp.getStatusCode()).headers(out).body(resp.getBody());
        } catch (Exception e) {
            log.warn("[ImageProxy] fetch failed url={}: {}", url, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }
}
