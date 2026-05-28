package com.nevinsight.admin.controller.v1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nevinsight.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 飞书应用 OAuth 2.0 网页授权（H5 场景）。
 *
 * 流程：
 *   1. 前端跳 GET /api/v1/feishu-auth/login
 *      → 302 跳飞书授权页 → 用户同意 → 飞书带 code 回 GET /api/v1/feishu-auth/callback?code=...
 *   2. 后端 callback 用 code + app_id + app_secret 换 user_access_token
 *   3. 用 user_access_token 换用户信息（user_id / name / avatar / mobile）
 *   4. 返回给前端，前端存 localStorage
 *
 * 配置（.env）：
 *   FEISHU_APP_ID         应用 ID（lark suite 应用后台拿）
 *   FEISHU_APP_SECRET     应用密钥
 *   FEISHU_OAUTH_REDIRECT 回调地址（飞书后台「重定向 URL」白名单要加这个）
 *                         如 http://localhost:3080/h5/benchmark
 *   FEISHU_REQUIRE_LOGIN  是否强制登录（默认 false，true 时未登录用户看不到 iframe）
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/feishu-auth")
@RequiredArgsConstructor
public class FeishuAuthController {

    @Value("${nevinsight.feishu.app-id:}")
    private String appId;

    @Value("${nevinsight.feishu.app-secret:}")
    private String appSecret;

    @Value("${nevinsight.feishu.oauth-redirect:}")
    private String redirectUri;

    @Value("${nevinsight.feishu.require-login:false}")
    private boolean requireLogin;

    private final RestTemplate http = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 给前端用 — 返回飞书登录 URL + 是否强制登录 */
    @GetMapping("/config")
    public ApiResponse<Map<String, Object>> config() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("configured", !appId.isEmpty() && !appSecret.isEmpty());
        out.put("requireLogin", requireLogin);
        out.put("loginUrl", buildLoginUrl());
        return ApiResponse.success(out);
    }

    /** 直接 302 跳飞书 */
    @GetMapping("/login")
    public ResponseEntity<Void> login() {
        if (appId.isEmpty()) {
            return ResponseEntity.status(500).build();
        }
        HttpHeaders h = new HttpHeaders();
        h.set("Location", buildLoginUrl());
        return new ResponseEntity<>(h, org.springframework.http.HttpStatus.FOUND);
    }

    /** OAuth 回调 — 前端用 ?code= 调这个，回 user 信息 */
    @GetMapping("/callback")
    public ApiResponse<Map<String, Object>> callback(@RequestParam String code) {
        if (appId.isEmpty() || appSecret.isEmpty()) {
            return ApiResponse.error(500, "飞书 App 未配置");
        }
        try {
            // 1. 拿 app_access_token (tenant_access_token 也可)
            String appToken = getAppAccessToken();
            if (appToken == null) {
                return ApiResponse.error(500, "获取 app_access_token 失败");
            }

            // 2. code → user_access_token
            String userToken = exchangeUserToken(appToken, code);
            if (userToken == null) {
                return ApiResponse.error(500, "code 换 user_access_token 失败（code 已用 / 已过期？）");
            }

            // 3. user_access_token → user info
            Map<String, Object> user = fetchUserInfo(userToken);
            if (user == null) {
                return ApiResponse.error(500, "拉用户信息失败");
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("user", user);
            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("[FeishuAuth] callback failed: {}", e.getMessage(), e);
            return ApiResponse.error(500, "飞书登录失败: " + e.getMessage());
        }
    }

    // ───────────────────────────────────────────────────────

    private String buildLoginUrl() {
        if (appId.isEmpty() || redirectUri.isEmpty()) return "";
        String redirect = URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);
        return "https://open.feishu.cn/open-apis/authen/v1/authorize"
                + "?app_id=" + appId
                + "&redirect_uri=" + redirect
                + "&response_type=code"
                + "&scope=";
    }

    private String getAppAccessToken() throws Exception {
        String url = "https://open.feishu.cn/open-apis/auth/v3/app_access_token/internal";
        Map<String, String> body = Map.of("app_id", appId, "app_secret", appSecret);
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> r = http.exchange(url, HttpMethod.POST,
                new HttpEntity<>(objectMapper.writeValueAsString(body), h), String.class);
        JsonNode n = objectMapper.readTree(r.getBody());
        if (n.path("code").asInt(-1) != 0) {
            log.warn("[FeishuAuth] app_access_token failed: {}", r.getBody());
            return null;
        }
        return n.path("app_access_token").asText();
    }

    private String exchangeUserToken(String appToken, String code) throws Exception {
        String url = "https://open.feishu.cn/open-apis/authen/v1/oidc/access_token";
        Map<String, String> body = Map.of("grant_type", "authorization_code", "code", code);
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set("Authorization", "Bearer " + appToken);
        ResponseEntity<String> r = http.exchange(url, HttpMethod.POST,
                new HttpEntity<>(objectMapper.writeValueAsString(body), h), String.class);
        JsonNode n = objectMapper.readTree(r.getBody());
        if (n.path("code").asInt(-1) != 0) {
            log.warn("[FeishuAuth] user_access_token failed: {}", r.getBody());
            return null;
        }
        return n.path("data").path("access_token").asText();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchUserInfo(String userToken) throws Exception {
        String url = "https://open.feishu.cn/open-apis/authen/v1/user_info";
        HttpHeaders h = new HttpHeaders();
        h.set("Authorization", "Bearer " + userToken);
        ResponseEntity<String> r = http.exchange(url, HttpMethod.GET, new HttpEntity<>(h), String.class);
        JsonNode n = objectMapper.readTree(r.getBody());
        if (n.path("code").asInt(-1) != 0) {
            log.warn("[FeishuAuth] user_info failed: {}", r.getBody());
            return null;
        }
        JsonNode d = n.path("data");
        Map<String, Object> u = new LinkedHashMap<>();
        u.put("openId", d.path("open_id").asText(""));
        u.put("unionId", d.path("union_id").asText(""));
        u.put("userId", d.path("user_id").asText(""));
        u.put("name", d.path("name").asText(""));
        u.put("enName", d.path("en_name").asText(""));
        u.put("avatar", d.path("avatar_url").asText(""));
        u.put("avatarThumb", d.path("avatar_thumb").asText(""));
        u.put("email", d.path("email").asText(""));
        u.put("mobile", d.path("mobile").asText(""));
        u.put("tenantKey", d.path("tenant_key").asText(""));
        log.info("[FeishuAuth] user logged in: {} ({})", u.get("name"), u.get("openId"));
        return u;
    }
}
