"""
汽车之家车型参数抓取。

策略：
1. 先试 httpx 调 mobile JSON API（car-web-api.autohome.com.cn）—— 快且稳
2. 若 JSON 失败/为空，回退 Playwright 渲染 config 页，提取 window.config / var config 变量

返回结构（每个车系一份）：
{
  "success": True/False,
  "series_id": "7172",
  "specs": [
    {"spec_id": "61234", "spec_name": "猛士M817 2026款 Ultimate",
     "param_category": "基本参数", "param_name": "厂商",
     "param_value": "猛士汽车"},
    ...
  ],
  "error": str | None,
  "source": "json_api" | "playwright"
}
"""
from __future__ import annotations

import asyncio
import json
import logging
import random
import re
from typing import Any

import httpx

log = logging.getLogger("autohome_scraper")

JSON_API_URL = "https://car-web-api.autohome.com.cn/car/param/getParamConf"
CONFIG_PAGE_URL = "https://car.autohome.com.cn/config/series/{seriesid}.html"

DEFAULT_HEADERS = {
    "User-Agent": "Mozilla/5.0 (iPhone; CPU iPhone OS 16_4 like Mac OS X) "
                  "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.4 "
                  "Mobile/15E148 Safari/604.1",
    "Accept": "application/json, text/plain, */*",
    "Accept-Language": "zh-CN,zh;q=0.9",
    "Referer": "https://car.autohome.com.cn/",
}


async def fetch_series(seriesid: str) -> dict[str, Any]:
    """Fetch params for one series with JSON API first, Playwright fallback."""
    # Strategy 1: JSON API
    try:
        data = await _fetch_json(seriesid)
        specs = _parse_response(data)
        if specs:
            return {"success": True, "series_id": seriesid, "specs": specs,
                    "error": None, "source": "json_api"}
        log.info("[autohome] series=%s json_api returned empty, fallback playwright", seriesid)
    except Exception as e:
        log.warning("[autohome] series=%s json_api failed: %s", seriesid, e)

    # Strategy 2: Playwright fallback
    try:
        specs = await _fetch_via_playwright(seriesid)
        if specs:
            return {"success": True, "series_id": seriesid, "specs": specs,
                    "error": None, "source": "playwright"}
        return {"success": False, "series_id": seriesid, "specs": [],
                "error": "playwright returned empty", "source": "playwright"}
    except Exception as e:
        log.error("[autohome] series=%s playwright failed: %s", seriesid, e)
        return {"success": False, "series_id": seriesid, "specs": [],
                "error": str(e), "source": "playwright"}


async def _fetch_json(seriesid: str) -> dict:
    params = {"_appid": "mobile", "pm": "2", "seriesid": str(seriesid)}
    async with httpx.AsyncClient(timeout=15.0, headers=DEFAULT_HEADERS) as client:
        resp = await client.get(JSON_API_URL, params=params)
        resp.raise_for_status()
        return resp.json()


async def _fetch_via_playwright(seriesid: str) -> list[dict]:
    """退路：用 httpx 拿 HTML（无需 JS 执行，config 是服务端模板渲染的）。
    名为 playwright 是历史遗留 —— 实测 HTML 就够，不必启浏览器。
    若 HTML 渲染失败再走真的 Playwright。"""
    url = CONFIG_PAGE_URL.format(seriesid=seriesid)
    async with httpx.AsyncClient(timeout=30.0, headers={
        **DEFAULT_HEADERS,
        "User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
                      "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    }, follow_redirects=True) as client:
        resp = await client.get(url)
        resp.raise_for_status()
        html = resp.text

    config = _extract_config_from_html(html)
    if not config:
        # 真的 Playwright 兜底（处理 JS 渲染场景）
        return await _fetch_via_real_playwright(seriesid)
    return _parse_response(config)


def _extract_config_from_html(html: str) -> dict | None:
    """从 `var config = {...};` 中提取 JSON，使用括号配对而不是贪婪正则。"""
    marker = "var config ="
    idx = html.find(marker)
    if idx < 0:
        return None
    # 找到 '{'
    start = html.find("{", idx)
    if start < 0:
        return None
    # 平衡括号
    depth = 0
    in_str = False
    esc = False
    end = -1
    for i in range(start, len(html)):
        c = html[i]
        if in_str:
            if esc:
                esc = False
            elif c == "\\":
                esc = True
            elif c == '"':
                in_str = False
        else:
            if c == '"':
                in_str = True
            elif c == "{":
                depth += 1
            elif c == "}":
                depth -= 1
                if depth == 0:
                    end = i
                    break
    if end < 0:
        return None
    raw = html[start:end + 1]
    try:
        return json.loads(raw)
    except json.JSONDecodeError as e:
        log.warning("[autohome] config JSON parse failed: %s", e)
        return None


async def _fetch_via_real_playwright(seriesid: str) -> list[dict]:
    """真用 Playwright，仅当纯 HTML 抓不到 config 时调用。"""
    from playwright.async_api import async_playwright  # type: ignore

    url = CONFIG_PAGE_URL.format(seriesid=seriesid)
    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=True, args=["--no-sandbox"])
        try:
            ctx = await browser.new_context(user_agent=DEFAULT_HEADERS["User-Agent"])
            page = await ctx.new_page()
            await page.goto(url, wait_until="domcontentloaded", timeout=60_000)
            html = await page.content()
            config = _extract_config_from_html(html)
            return _parse_response(config) if config else []
        finally:
            await browser.close()


_SPAN_PATTERN = re.compile(r"<span[^>]*class=['\"]hs_kw[^'\"]*['\"][^>]*></span>")
_TAG_PATTERN = re.compile(r"<[^>]+>")


def _clean_value(v: str) -> str:
    """去掉 autohome 反爬注入的 <span class='hs_kwXX_...'></span> 占位，并清剩余 HTML 标签。
    被混淆掉的字符暂以 '·' 占位（v2 可解 font-face glyph 映射还原）。"""
    if not isinstance(v, str):
        return str(v) if v is not None else ""
    v = _SPAN_PATTERN.sub("·", v)
    v = _TAG_PATTERN.sub("", v)
    return v.strip()


def _parse_response(data: dict) -> list[dict]:
    """Flatten autohome response. Handles both /car/param/getParamConf JSON
    and rendered window.config structures."""
    if not isinstance(data, dict):
        return []
    result = data.get("result") or data.get("data") or data
    if not isinstance(result, dict):
        return []

    paramtypeitems = (result.get("paramtypeitems")
                      or result.get("paramtypes")
                      or result.get("paramTypeItems")
                      or [])
    speclist = (result.get("speclist")
                or result.get("specs")
                or result.get("specList")
                or [])

    spec_id_to_name: dict[str, str] = {}
    for s in speclist:
        if not isinstance(s, dict):
            continue
        sid = str(s.get("specid") or s.get("id") or s.get("specId") or "")
        sname = s.get("specname") or s.get("name") or s.get("specName") or ""
        if sid:
            spec_id_to_name[sid] = _clean_value(sname)

    out: list[dict] = []
    for cat in paramtypeitems:
        if not isinstance(cat, dict):
            continue
        category = _clean_value(cat.get("name", ""))
        params_list = (cat.get("paramitems")
                       or cat.get("params")
                       or cat.get("paramItems")
                       or [])
        for param in params_list:
            if not isinstance(param, dict):
                continue
            param_name = _clean_value(param.get("name", ""))
            values = (param.get("valueitems")
                      or param.get("values")
                      or param.get("valueItems")
                      or [])
            for v in values:
                if not isinstance(v, dict):
                    continue
                sid = str(v.get("specid") or v.get("id") or v.get("specId") or "")
                val = v.get("value", "")
                if val is None:
                    val = ""
                if not sid or not param_name:
                    continue
                out.append({
                    "spec_id": sid,
                    "spec_name": spec_id_to_name.get(sid, ""),
                    "param_category": category,
                    "param_name": param_name,
                    "param_value": _clean_value(str(val))[:500],
                })
    return out


async def scrape_many(
    series_ids: list[str],
    delay_seconds: tuple[float, float] = (10.0, 18.0),
) -> list[dict]:
    """Sequential scrape with random delay between series.

    delay 10-18s 是 autohome 反爬阈值：低于 8s 连续请求会触发 SSL handshake
    failure 或 var config 缺失（返回反爬页）。20 车系约 4-6 分钟。
    """
    results = []
    for i, sid in enumerate(series_ids):
        if i > 0:
            await asyncio.sleep(random.uniform(*delay_seconds))
        r = await fetch_series(sid)
        log.info("[autohome] sid=%s success=%s rows=%d source=%s",
                 sid, r["success"], len(r["specs"]), r.get("source"))
        results.append(r)
    return results
