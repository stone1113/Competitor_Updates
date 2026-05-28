# -*- coding: utf-8 -*-
# Copyright (c) 2025 relakkes@gmail.com
#
# This file is part of MediaCrawler project.
# Repository: https://github.com/NanmiCoder/MediaCrawler/blob/main/media_platform/weibo/login.py
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


# -*- coding: utf-8 -*-
# @Author  : relakkes@gmail.com
# @Time    : 2023/12/23 15:42
# @Desc    : Weibo login implementation

import asyncio
import functools
import sys
from typing import Optional

from playwright.async_api import BrowserContext, Page
from tenacity import (RetryError, retry, retry_if_result, stop_after_attempt,
                      wait_fixed)

import config
from base.base_crawler import AbstractLogin
from tools import utils


class WeiboLogin(AbstractLogin):
    def __init__(self,
                 login_type: str,
                 browser_context: BrowserContext,
                 context_page: Page,
                 login_phone: Optional[str] = "",
                 cookie_str: str = ""
                 ):
        config.LOGIN_TYPE = login_type
        self.browser_context = browser_context
        self.context_page = context_page
        self.login_phone = login_phone
        self.cookie_str = cookie_str
        self.weibo_sso_login_url = "https://passport.weibo.com/sso/signin?entry=miniblog&source=miniblog"

    async def begin(self):
        """Start login weibo"""
        utils.logger.info("[WeiboLogin.begin] Begin login weibo ...")
        if config.LOGIN_TYPE == "qrcode":
            await self.login_by_qrcode()
        elif config.LOGIN_TYPE == "phone":
            await self.login_by_mobile()
        elif config.LOGIN_TYPE == "cookie":
            await self.login_by_cookies()
        else:
            raise ValueError(
                "[WeiboLogin.begin] Invalid Login Type Currently only supported qrcode or phone or cookie ...")


    @retry(stop=stop_after_attempt(600), wait=wait_fixed(1), retry=retry_if_result(lambda value: value is False))
    async def check_login_state(self, no_logged_in_session: str) -> bool:
        """
            Check if the current login status is successful and return True otherwise return False
            retry decorator will retry 20 times if the return value is False, and the retry interval is 1 second
            if max retry times reached, raise RetryError
        """
        current_cookie = await self.browser_context.cookies()
        _, cookie_dict = utils.convert_cookies(current_cookie)
        if cookie_dict.get("SSOLoginState"):
            return True
        current_web_session = cookie_dict.get("WBPSESS")
        if current_web_session != no_logged_in_session:
            return True
        return False

    async def login_by_qrcode(self):
        """login weibo website and keep webdriver login state"""
        utils.logger.info("[WeiboLogin.login_by_qrcode] Begin login weibo by qrcode ...")
        await self.context_page.goto(self.weibo_sso_login_url, timeout=60000)
        await self.context_page.wait_for_load_state("networkidle")

        # 移动端 UA 下默认显示手机号登录，需要先点击"扫码登录"切换
        try:
            scan_btn = self.context_page.locator("xpath=//span[contains(text(),'扫码登录')]")
            if await scan_btn.count() > 0:
                utils.logger.info("[WeiboLogin.login_by_qrcode] 检测到移动端页面，点击扫码登录...")
                await scan_btn.click()
                await asyncio.sleep(2)
        except Exception as e:
            utils.logger.info(f"[WeiboLogin.login_by_qrcode] 扫码登录按钮未找到，继续: {e}")

        # find login qrcode
        qrcode_img_selector = "xpath=//img[@class='w-full h-full']"
        base64_qrcode_img = await utils.find_login_qrcode(
            self.context_page,
            selector=qrcode_img_selector
        )
        if not base64_qrcode_img:
            utils.logger.info("[WeiboLogin.login_by_qrcode] login failed , have not found qrcode please check ....")
            sys.exit()

        # show login qrcode
        partial_show_qrcode = functools.partial(utils.show_qrcode, base64_qrcode_img)
        asyncio.get_running_loop().run_in_executor(executor=None, func=partial_show_qrcode)

        utils.logger.info(f"[WeiboLogin.login_by_qrcode] Waiting for scan code login, remaining time is 20s")

        # get not logged session
        current_cookie = await self.browser_context.cookies()
        _, cookie_dict = utils.convert_cookies(current_cookie)
        no_logged_in_session = cookie_dict.get("WBPSESS")

        try:
            await self.check_login_state(no_logged_in_session)
        except RetryError:
            utils.logger.info("[WeiboLogin.login_by_qrcode] Login weibo failed by qrcode login method ...")
            sys.exit()

        wait_redirect_seconds = 5
        utils.logger.info(
            f"[WeiboLogin.login_by_qrcode] Login successful then wait for {wait_redirect_seconds} seconds redirect ...")
        await asyncio.sleep(wait_redirect_seconds)

    async def login_by_mobile(self):
        """Open the Weibo login page and wait for manual phone login."""
        utils.logger.info("[WeiboLogin.login_by_mobile] Begin login weibo by mobile/manual login ...")
        await self.context_page.goto(self.weibo_sso_login_url, timeout=60000)
        await self.context_page.wait_for_load_state("domcontentloaded")

        current_cookie = await self.browser_context.cookies()
        _, cookie_dict = utils.convert_cookies(current_cookie)
        no_logged_in_session = cookie_dict.get("WBPSESS")

        utils.logger.info("[WeiboLogin.login_by_mobile] Waiting for manual phone login in browser ...")
        try:
            await self.check_login_state(no_logged_in_session)
        except RetryError:
            utils.logger.info("[WeiboLogin.login_by_mobile] Login weibo failed by mobile/manual login method ...")
            sys.exit()

        wait_redirect_seconds = 5
        utils.logger.info(
            f"[WeiboLogin.login_by_mobile] Login successful then wait for {wait_redirect_seconds} seconds redirect ...")
        await asyncio.sleep(wait_redirect_seconds)

    async def login_by_cookies(self):
        utils.logger.info("[WeiboLogin.login_by_qrcode] Begin login weibo by cookie ...")
        for key, value in utils.convert_str_cookie_to_dict(self.cookie_str).items():
            await self.browser_context.add_cookies([{
                'name': key,
                'value': value,
                'domain': ".weibo.cn",
                'path': "/"
            }])
