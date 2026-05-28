# -*- coding: utf-8 -*-
# Copyright (c) 2025 relakkes@gmail.com
#
# This file is part of MediaCrawler project.
# Repository: https://github.com/NanmiCoder/MediaCrawler/blob/main/media_platform/kuaishou/core.py
# GitHub: https://github.com/NanmiCoder
# Licensed under NON-COMMERCIAL LEARNING LICENSE 1.1
#

# 声明：本代码仅供学习和研究目的使用。使用者应遵守以下原则：
# 1. 不得用于任何商业用途。
# 2. 使用时应遵守目标平台的使用条款和robots.txt规则。
# 3. 不得进行大规模爬取或对平台造成运营干扰。
# 4. 应合理控制请求频率，避免给目标平台带来不必要的负担。
# 5. 不得用于任何非法或不当的用途。
#
# 详细许可条款请参阅项目根目录下的LICENSE文件。
# 使用本代码即表示您同意遵守上述原则和LICENSE中的所有条款。


import asyncio
import os
# import random  # Removed as we now use fixed config.CRAWLER_MAX_SLEEP_SEC intervals
import time
from asyncio import Task
from typing import Dict, List, Optional, Tuple
from urllib.parse import quote

from playwright.async_api import (
    BrowserContext,
    BrowserType,
    Page,
    Playwright,
    async_playwright,
)

import config
from base.base_crawler import AbstractCrawler
from model.m_kuaishou import VideoUrlInfo, CreatorUrlInfo
from proxy.proxy_ip_pool import IpInfoModel, create_ip_pool
from store import kuaishou as kuaishou_store
from tools import utils
from tools.cdp_browser import CDPBrowserManager
from var import comment_tasks_var, crawler_type_var, source_keyword_var

from .client import KuaiShouClient
from .exception import DataFetchError
from .help import parse_video_info_from_url, parse_creator_info_from_url
from .login import KuaishouLogin


class KuaishouCrawler(AbstractCrawler):
    context_page: Page
    ks_client: KuaiShouClient
    browser_context: BrowserContext
    cdp_manager: Optional[CDPBrowserManager]

    def __init__(self):
        self.index_url = "https://www.kuaishou.com"
        self.user_agent = utils.get_user_agent()
        self.cdp_manager = None
        self.ip_proxy_pool = None  # Proxy IP pool, used for automatic proxy refresh

    async def start(self):
        playwright_proxy_format, httpx_proxy_format = None, None
        if config.ENABLE_IP_PROXY:
            self.ip_proxy_pool = await create_ip_pool(
                config.IP_PROXY_POOL_COUNT, enable_validate_ip=True
            )
            ip_proxy_info: IpInfoModel = await self.ip_proxy_pool.get_proxy()
            playwright_proxy_format, httpx_proxy_format = utils.format_proxy_info(
                ip_proxy_info
            )

        async with async_playwright() as playwright:
            # Select startup mode based on configuration
            if config.ENABLE_CDP_MODE:
                utils.logger.info("[KuaishouCrawler] Launching browser using CDP mode")
                self.browser_context = await self.launch_browser_with_cdp(
                    playwright,
                    playwright_proxy_format,
                    self.user_agent,
                    headless=config.CDP_HEADLESS,
                )
            else:
                utils.logger.info("[KuaishouCrawler] Launching browser using standard mode")
                # Launch a browser context.
                chromium = playwright.chromium
                self.browser_context = await self.launch_browser(
                    chromium, None, self.user_agent, headless=config.HEADLESS
                )
                # stealth.min.js is a js script to prevent the website from detecting the crawler.
                await self.browser_context.add_init_script(path="libs/stealth.min.js")


            self.context_page = await self.browser_context.new_page()
            await self.context_page.goto(f"{self.index_url}?isHome=1", timeout=120000)

            # Create a client to interact with the kuaishou website.
            self.ks_client = await self.create_ks_client(httpx_proxy_format)
            if not await self.ks_client.pong():
                login_obj = KuaishouLogin(
                    login_type=config.LOGIN_TYPE,
                    login_phone=httpx_proxy_format,
                    browser_context=self.browser_context,
                    context_page=self.context_page,
                    cookie_str=config.COOKIES,
                )
                await login_obj.begin()
                await self.ks_client.update_cookies(
                    browser_context=self.browser_context
                )

            crawler_type_var.set(config.CRAWLER_TYPE)
            if config.CRAWLER_TYPE == "search":
                # Search for videos and retrieve their comment information.
                await self.search()
            elif config.CRAWLER_TYPE == "detail":
                # Get the information and comments of the specified post
                await self.get_specified_videos()
            elif config.CRAWLER_TYPE == "creator":
                # Get creator's information and their videos and comments
                await self.get_creators_and_videos()
            else:
                pass

            utils.logger.info("[KuaishouCrawler.start] Kuaishou Crawler finished ...")

    async def _search_by_browser(self, keyword: str) -> List[Dict]:
        """Navigate to search page and intercept REST API search results from the browser.
        Returns list of feed items.
        """
        search_url = f"{self.index_url}/search/video?searchKey={quote(keyword)}"
        collected_feeds: List[Dict] = []
        first_response_event = asyncio.Event()

        async def handle_response(response):
            if "/rest/v/search/feed" not in response.url:
                return
            try:
                data = await response.json()
                if data.get("result") == 1:
                    feeds = data.get("feeds", [])
                    if feeds:
                        collected_feeds.extend(feeds)
                        utils.logger.info(
                            f"[KuaishouCrawler._search_by_browser] Intercepted {len(feeds)} videos, total: {len(collected_feeds)}"
                        )
                        # Log first feed structure for debugging
                        if len(collected_feeds) == len(feeds):
                            first_keys = list(feeds[0].keys()) if feeds else []
                            utils.logger.info(
                                f"[KuaishouCrawler._search_by_browser] Feed item keys: {first_keys}"
                            )
                        first_response_event.set()
                else:
                    utils.logger.warning(
                        f"[KuaishouCrawler._search_by_browser] Search API result={data.get('result')}"
                    )
            except Exception as e:
                utils.logger.debug(f"[KuaishouCrawler._search_by_browser] Parse error: {e}")

        self.context_page.on("response", handle_response)
        try:
            utils.logger.info(f"[KuaishouCrawler._search_by_browser] Navigating to: {search_url}")
            await self.context_page.goto(search_url, wait_until="domcontentloaded", timeout=120000)
            await asyncio.sleep(3)

            # Wait for initial search results
            try:
                await asyncio.wait_for(first_response_event.wait(), timeout=10)
            except asyncio.TimeoutError:
                utils.logger.warning(
                    f"[KuaishouCrawler._search_by_browser] No search results for keyword: {keyword}"
                )
                return []

            # Scroll to load more results
            for scroll_round in range(5):
                if len(collected_feeds) >= config.CRAWLER_MAX_NOTES_COUNT:
                    break
                prev_count = len(collected_feeds)
                await self.context_page.evaluate("window.scrollTo(0, document.body.scrollHeight)")
                await asyncio.sleep(2)
                if len(collected_feeds) == prev_count:
                    break

        except Exception as e:
            utils.logger.error(f"[KuaishouCrawler._search_by_browser] Error: {e}")
        finally:
            self.context_page.remove_listener("response", handle_response)

        return collected_feeds

    async def _save_search_feeds(self, feeds: List[Dict]) -> List[str]:
        """Save search feed items and return video IDs.
        Handles both GraphQL and REST API feed formats.
        """
        video_id_list: List[str] = []
        for feed in feeds:
            # REST API format: feed may have "photo" or direct fields
            photo_info = feed.get("photo", {})
            video_id = photo_info.get("id") if photo_info else feed.get("id")
            if not video_id:
                utils.logger.debug(f"[KuaishouCrawler._save_search_feeds] Skipping feed with no video_id, keys: {list(feed.keys())}")
                continue
            video_id_list.append(video_id)
            await kuaishou_store.update_kuaishou_video(video_item=feed)
        return video_id_list

    async def search(self):
        utils.logger.info("[KuaishouCrawler.search] Begin search kuaishou keywords (browser mode)")
        for keyword in config.KEYWORDS.split(","):
            source_keyword_var.set(keyword)
            utils.logger.info(f"[KuaishouCrawler.search] Current search keyword: {keyword}")

            feeds = await self._search_by_browser(keyword)

            if not feeds:
                utils.logger.warning(f"[KuaishouCrawler.search] No videos found for keyword: {keyword}, skipping")
                continue

            # Limit to max count
            if len(feeds) > config.CRAWLER_MAX_NOTES_COUNT:
                feeds = feeds[:config.CRAWLER_MAX_NOTES_COUNT]

            utils.logger.info(f"[KuaishouCrawler.search] Got {len(feeds)} videos for keyword: {keyword}")

            video_id_list = await self._save_search_feeds(feeds)

            # Fetch comments via browser for each video
            await self.batch_get_video_comments(video_id_list)

            # Sleep between keywords
            await asyncio.sleep(config.CRAWLER_MAX_SLEEP_SEC)
            utils.logger.info(f"[KuaishouCrawler.search] Finished keyword: {keyword}")

    async def get_specified_videos(self):
        """Get the information and comments of the specified post"""
        utils.logger.info("[KuaishouCrawler.get_specified_videos] Parsing video URLs...")
        video_ids = []
        for video_url in config.KS_SPECIFIED_ID_LIST:
            try:
                video_info = parse_video_info_from_url(video_url)
                video_ids.append(video_info.video_id)
                utils.logger.info(f"Parsed video ID: {video_info.video_id} from {video_url}")
            except ValueError as e:
                utils.logger.error(f"Failed to parse video URL: {e}")
                continue

        semaphore = asyncio.Semaphore(config.MAX_CONCURRENCY_NUM)
        task_list = [
            self.get_video_info_task(video_id=video_id, semaphore=semaphore)
            for video_id in video_ids
        ]
        video_details = await asyncio.gather(*task_list)
        for video_detail in video_details:
            if video_detail is not None:
                await kuaishou_store.update_kuaishou_video(video_detail)
        await self.batch_get_video_comments(video_ids)

    async def get_video_info_task(
        self, video_id: str, semaphore: asyncio.Semaphore
    ) -> Optional[Dict]:
        """Get video detail task"""
        async with semaphore:
            try:
                result = await self.ks_client.get_video_info(video_id)

                # Sleep after fetching video details
                await asyncio.sleep(config.CRAWLER_MAX_SLEEP_SEC)
                utils.logger.info(f"[KuaishouCrawler.get_video_info_task] Sleeping for {config.CRAWLER_MAX_SLEEP_SEC} seconds after fetching video details {video_id}")

                utils.logger.info(
                    f"[KuaishouCrawler.get_video_info_task] Get video_id:{video_id} info result: {result} ..."
                )
                return result.get("visionVideoDetail")
            except DataFetchError as ex:
                utils.logger.error(
                    f"[KuaishouCrawler.get_video_info_task] Get video detail error: {ex}"
                )
                return None
            except KeyError as ex:
                utils.logger.error(
                    f"[KuaishouCrawler.get_video_info_task] have not fund video detail video_id:{video_id}, err: {ex}"
                )
                return None

    async def batch_get_video_comments(self, video_id_list: List[str]):
        """
        Batch get video comments using browser-based interception.
        Comments are fetched sequentially since we use a single browser page.
        """
        if not config.ENABLE_GET_COMMENTS:
            utils.logger.info(
                f"[KuaishouCrawler.batch_get_video_comments] Crawling comment mode is not enabled"
            )
            return

        utils.logger.info(
            f"[KuaishouCrawler.batch_get_video_comments] video ids:{video_id_list}"
        )
        for video_id in video_id_list:
            try:
                await self.get_comments_by_browser(video_id)
            except Exception as e:
                utils.logger.error(
                    f"[KuaishouCrawler.get_comments_by_browser] video_id: {video_id} error: {e}"
                )
            await asyncio.sleep(config.CRAWLER_MAX_SLEEP_SEC)

    async def get_comments_by_browser(self, video_id: str):
        """
        Navigate to video page and intercept comment API responses from the browser.
        This bypasses CAPTCHA since requests come from the real browser context.
        """
        video_url = f"{self.index_url}/short-video/{video_id}"
        collected_comments: List[Dict] = []
        first_response_event = asyncio.Event()
        max_count = config.CRAWLER_MAX_COMMENTS_COUNT_SINGLENOTES

        async def handle_response(response):
            url = response.url

            # Handle REST API comments
            if "/rest/v/photo/comment/list" in url or "/rest/v/photo/comment/sublist" in url:
                try:
                    data = await response.json()
                    if data.get("result") != 1:
                        return
                    if "/comment/list" in url:
                        comments = data.get("rootCommentsV2", [])
                        if comments:
                            collected_comments.extend(comments)
                            await kuaishou_store.batch_update_ks_video_comments(video_id, comments)
                            first_response_event.set()
                    elif "/comment/sublist" in url:
                        sub_comments = data.get("subCommentsV2", [])
                        if sub_comments:
                            collected_comments.extend(sub_comments)
                            await kuaishou_store.batch_update_ks_video_comments(video_id, sub_comments)
                except Exception:
                    pass
                return

            # Handle GraphQL comments (video detail page uses GraphQL for comments)
            if "/graphql" not in url:
                return
            try:
                data = await response.json()
                gql_data = data.get("data", {})
                utils.logger.info(
                    f"[KuaishouCrawler.get_comments_by_browser] GraphQL keys: {list(gql_data.keys())}"
                )

                # First-level comments via GraphQL
                comment_list = gql_data.get("visionCommentList", {})
                if comment_list:
                    utils.logger.info(
                        f"[KuaishouCrawler.get_comments_by_browser] visionCommentList keys: {list(comment_list.keys())}, result={comment_list.get('result')}"
                    )
                if comment_list and (comment_list.get("rootComments") or comment_list.get("rootCommentsV2")):
                    comments = comment_list.get("rootComments") or comment_list.get("rootCommentsV2") or []
                    if comments:
                        collected_comments.extend(comments)
                        utils.logger.info(
                            f"[KuaishouCrawler.get_comments_by_browser] Intercepted {len(comments)} GraphQL comments for video {video_id}, total: {len(collected_comments)}"
                        )
                        await kuaishou_store.batch_update_ks_video_comments(video_id, comments)
                        first_response_event.set()

                # Sub-comments via GraphQL
                sub_comment_list = gql_data.get("visionSubCommentList", {})
                if sub_comment_list and (sub_comment_list.get("subComments") or sub_comment_list.get("subCommentsV2")):
                    sub_comments = sub_comment_list.get("subComments", [])
                    if sub_comments:
                        collected_comments.extend(sub_comments)
                        utils.logger.info(
                            f"[KuaishouCrawler.get_comments_by_browser] Intercepted {len(sub_comments)} GraphQL sub-comments for video {video_id}"
                        )
                        await kuaishou_store.batch_update_ks_video_comments(video_id, sub_comments)

            except Exception:
                pass

        self.context_page.on("response", handle_response)
        try:
            utils.logger.info(
                f"[KuaishouCrawler.get_comments_by_browser] Navigating to video: {video_url}"
            )
            # Use domcontentloaded instead of networkidle — video streaming prevents networkidle
            await self.context_page.goto(video_url, wait_until="domcontentloaded", timeout=120000)
            # Wait for page to render
            await asyncio.sleep(3)

            # Scroll down to trigger comment section loading
            for i in range(3):
                await self.context_page.evaluate("window.scrollBy(0, 600)")
                await asyncio.sleep(1)

            # Wait for comment API response
            try:
                await asyncio.wait_for(first_response_event.wait(), timeout=10)
            except asyncio.TimeoutError:
                utils.logger.warning(
                    f"[KuaishouCrawler.get_comments_by_browser] No comments intercepted for video {video_id}, page may have no comments"
                )
                return

            # Scroll more to load additional comments
            scroll_rounds = 0
            max_scroll_rounds = 10
            while len(collected_comments) < max_count and scroll_rounds < max_scroll_rounds:
                prev_count = len(collected_comments)
                await self.context_page.evaluate("window.scrollTo(0, document.body.scrollHeight)")
                await asyncio.sleep(2)
                scroll_rounds += 1
                if len(collected_comments) == prev_count:
                    utils.logger.info(
                        f"[KuaishouCrawler.get_comments_by_browser] No more comments after scroll round {scroll_rounds}, total: {len(collected_comments)}"
                    )
                    break

            utils.logger.info(
                f"[KuaishouCrawler.get_comments_by_browser] Finished video {video_id}, collected {len(collected_comments)} comments"
            )
        except Exception as e:
            utils.logger.error(
                f"[KuaishouCrawler.get_comments_by_browser] Navigation error for video {video_id}: {e}"
            )
        finally:
            self.context_page.remove_listener("response", handle_response)

    async def create_ks_client(self, httpx_proxy: Optional[str]) -> KuaiShouClient:
        """Create ks client"""
        utils.logger.info(
            "[KuaishouCrawler.create_ks_client] Begin create kuaishou API client ..."
        )
        cookie_str, cookie_dict = utils.convert_cookies(
            await self.browser_context.cookies()
        )
        ks_client_obj = KuaiShouClient(
            proxy=httpx_proxy,
            headers={
                "User-Agent": self.user_agent,
                "Cookie": cookie_str,
                "Origin": self.index_url,
                "Referer": self.index_url,
                "Content-Type": "application/json;charset=UTF-8",
            },
            playwright_page=self.context_page,
            cookie_dict=cookie_dict,
            proxy_ip_pool=self.ip_proxy_pool,  # Pass proxy pool for automatic refresh
        )
        return ks_client_obj

    async def launch_browser(
        self,
        chromium: BrowserType,
        playwright_proxy: Optional[Dict],
        user_agent: Optional[str],
        headless: bool = True,
    ) -> BrowserContext:
        """Launch browser and create browser context"""
        utils.logger.info(
            "[KuaishouCrawler.launch_browser] Begin create browser context ..."
        )
        # 1. 不再用 channel="chrome"（系统 Chrome 可能太旧导致 SSL 握手失败 ERR_SSL_VERSION_OR_CIPHER_MISMATCH）
        # 2. --no-proxy-server：忽略系统/环境变量里的代理（Clash 等）。kuaishou 走代理时 TLS 协商会失败
        # 3. --ignore-certificate-errors：兜底证书校验
        chromium_args = [
            "--no-proxy-server",
            "--ignore-certificate-errors",
            "--ignore-certificate-errors-spki-list",
            "--disable-features=BlockInsecurePrivateNetworkRequests",
        ]
        if config.SAVE_LOGIN_STATE:
            user_data_dir = os.path.join(
                os.getcwd(), "browser_data", config.USER_DATA_DIR % config.PLATFORM
            )  # type: ignore
            browser_context = await chromium.launch_persistent_context(
                user_data_dir=user_data_dir,
                accept_downloads=True,
                headless=headless,
                proxy=playwright_proxy,  # type: ignore
                viewport={"width": 1920, "height": 1080},
                user_agent=user_agent,
                ignore_https_errors=True,
                args=chromium_args,
            )
            return browser_context
        else:
            browser = await chromium.launch(
                headless=headless,
                proxy=playwright_proxy,  # type: ignore
                args=chromium_args,
            )
            browser_context = await browser.new_context(
                viewport={"width": 1920, "height": 1080},
                user_agent=user_agent,
                ignore_https_errors=True,
            )
            return browser_context

    async def launch_browser_with_cdp(
        self,
        playwright: Playwright,
        playwright_proxy: Optional[Dict],
        user_agent: Optional[str],
        headless: bool = True,
    ) -> BrowserContext:
        """
        Launch browser using CDP mode
        """
        try:
            self.cdp_manager = CDPBrowserManager()
            browser_context = await self.cdp_manager.launch_and_connect(
                playwright=playwright,
                playwright_proxy=playwright_proxy,
                user_agent=user_agent,
                headless=headless,
            )

            # Display browser information
            browser_info = await self.cdp_manager.get_browser_info()
            utils.logger.info(f"[KuaishouCrawler] CDP browser info: {browser_info}")

            return browser_context

        except Exception as e:
            utils.logger.error(
                f"[KuaishouCrawler] CDP mode launch failed, fallback to standard mode: {e}"
            )
            # Fallback to standard mode
            chromium = playwright.chromium
            return await self.launch_browser(
                chromium, playwright_proxy, user_agent, headless
            )

    async def get_creators_and_videos(self) -> None:
        """Get creator's videos and retrieve their comment information."""
        utils.logger.info(
            "[KuaiShouCrawler.get_creators_and_videos] Begin get kuaishou creators"
        )
        for creator_url in config.KS_CREATOR_ID_LIST:
            try:
                # Parse creator URL to get user_id
                creator_info: CreatorUrlInfo = parse_creator_info_from_url(creator_url)
                utils.logger.info(f"[KuaiShouCrawler.get_creators_and_videos] Parse creator URL info: {creator_info}")
                user_id = creator_info.user_id

                # get creator detail info from web html content
                createor_info: Dict = await self.ks_client.get_creator_info(user_id=user_id)
                if createor_info:
                    await kuaishou_store.save_creator(user_id, creator=createor_info)
            except ValueError as e:
                utils.logger.error(f"[KuaiShouCrawler.get_creators_and_videos] Failed to parse creator URL: {e}")
                continue

            # Get all video information of the creator
            all_video_list = await self.ks_client.get_all_videos_by_creator(
                user_id=user_id,
                crawl_interval=config.CRAWLER_MAX_SLEEP_SEC,
                callback=self.fetch_creator_video_detail,
            )

            video_ids = [
                video_item.get("photo", {}).get("id") for video_item in all_video_list
            ]
            await self.batch_get_video_comments(video_ids)

    async def fetch_creator_video_detail(self, video_list: List[Dict]):
        """
        Concurrently obtain the specified post list and save the data
        """
        semaphore = asyncio.Semaphore(config.MAX_CONCURRENCY_NUM)
        task_list = [
            self.get_video_info_task(post_item.get("photo", {}).get("id"), semaphore)
            for post_item in video_list
        ]

        video_details = await asyncio.gather(*task_list)
        for video_detail in video_details:
            if video_detail is not None:
                await kuaishou_store.update_kuaishou_video(video_detail)

    async def close(self):
        """Close browser context"""
        # If using CDP mode, need special handling
        if self.cdp_manager:
            await self.cdp_manager.cleanup()
            self.cdp_manager = None
        else:
            await self.browser_context.close()
        utils.logger.info("[KuaishouCrawler.close] Browser context closed ...")
