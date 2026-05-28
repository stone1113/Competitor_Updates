"""
Task runner: spawn mediacrawler subprocess and track state.

Each /crawl request creates a Task with a UUID, kicked off via asyncio.create_task.
The subprocess writes directly to MySQL; we just monitor exit code + stdout tail.
"""
from __future__ import annotations

import asyncio
import os
import re
import sys
import time
import uuid
from collections import deque
from dataclasses import dataclass, field
from enum import Enum
from pathlib import Path
from typing import Optional

CRAWLER_DIR = Path(__file__).parent / "mediacrawler"
PROJECT_ROOT = Path(__file__).resolve().parent.parent
PYTHON = sys.executable
LOG_TAIL_LINES = 200
DEFAULT_TIMEOUT_SEC = 60 * 60  # 1 hour


class TaskStatus(str, Enum):
    PENDING = "PENDING"
    RUNNING = "RUNNING"
    WAITING_LOGIN = "WAITING_LOGIN"  # cookie 失效，等待用户扫码登录
    SUCCESS = "SUCCESS"
    FAILED = "FAILED"
    TIMEOUT = "TIMEOUT"


# Wait up to this long for the user to scan a re-login QR code
LOGIN_WAIT_SEC = int(os.getenv("LOGIN_WAIT_SEC", "300"))  # 5 min default

# Track the most-recent login task per platform so concurrent failures share one re-login
_latest_login_task: dict[str, str] = {}


@dataclass
class Task:
    task_id: str
    platform: str
    brand: str
    keywords: list[str]
    max_notes: int
    enable_comments: bool
    login_type: str
    mode: str = "search"           # search | creator
    creator_ids: list[str] = field(default_factory=list)
    status: TaskStatus = TaskStatus.PENDING
    created_at: float = field(default_factory=time.time)
    started_at: Optional[float] = None
    finished_at: Optional[float] = None
    exit_code: Optional[int] = None
    error_message: Optional[str] = None
    log_tail: deque = field(default_factory=lambda: deque(maxlen=LOG_TAIL_LINES))

    def to_dict(self) -> dict:
        return {
            "task_id": self.task_id,
            "platform": self.platform,
            "brand": self.brand,
            "mode": self.mode,
            "keywords": self.keywords,
            "creator_ids": self.creator_ids,
            "max_notes": self.max_notes,
            "enable_comments": self.enable_comments,
            "login_type": self.login_type,
            "status": self.status.value,
            "created_at": self.created_at,
            "started_at": self.started_at,
            "finished_at": self.finished_at,
            "duration_sec": (
                (self.finished_at or time.time()) - self.started_at
                if self.started_at else None
            ),
            "exit_code": self.exit_code,
            "error_message": self.error_message,
            "log_tail": list(self.log_tail),
        }


_tasks: dict[str, Task] = {}
_platform_locks: dict[str, asyncio.Lock] = {}

# Per-platform cooldown to avoid spawning multiple relogin attempts for the same platform
_last_relogin_at: dict[str, float] = {}
_RELOGIN_COOLDOWN_SEC = 300  # 5 min


def _has_browser_session(platform: str) -> bool:
    """Check whether MediaCrawler has stored a persistent browser session for this platform."""
    user_data = CRAWLER_DIR / "browser_data" / f"{platform}_user_data_dir"
    if not user_data.exists():
        return False
    # A populated dir = previously logged in
    try:
        return any(user_data.iterdir())
    except OSError:
        return False


_AUTH_STRONG_SUBSTRINGS = [
    "login state result: false",
    "cookie may be invalid",
    "check login state failed",
    "未登录",
    "请登录",
    "请重新登录",
    "请先登录",
]
# 用正则边界，避免匹配 19 位数字 ID（如评论 ID）里巧合的子串
_AUTH_STRONG_REGEX = re.compile(
    r"\b(401|403)\b|"                        # 独立 HTTP 状态码
    r"\bunauthorized\b|"
    r"\bforbidden\b|"
    r"login\s*(failed|required|expired)|"    # login failed / required / expired
    r"cookie\s*(expired|invalid)",
    re.IGNORECASE,
)


def _looks_like_auth_failure(task: "Task") -> bool:
    """Heuristic: detect cookie/login issues even when subprocess returns exit 0
    (MediaCrawler swallows xhs DataFetchError + login_state=False)."""
    log_blob = "\n".join(task.log_tail)
    log_lower = log_blob.lower()
    err = (task.error_message or "").lower()

    # Strong: substring matches
    if any(s in log_lower for s in _AUTH_STRONG_SUBSTRINGS):
        return True
    # Strong: regex with word boundary (avoid matching inside 19-digit IDs)
    if _AUTH_STRONG_REGEX.search(log_blob):
        return True

    # Weak: only on quick-fail + non-zero exit + timeout text
    # 不收 "登录" — 因为 "登录成功" 也含此字
    if task.exit_code is not None and task.exit_code != 0:
        if task.started_at and task.finished_at and (task.finished_at - task.started_at) < 90:
            if "timeout" in err or "timeout" in log_lower:
                return True
    return False


def list_tasks(limit: int = 50) -> list[dict]:
    items = sorted(_tasks.values(), key=lambda t: t.created_at, reverse=True)
    return [t.to_dict() for t in items[:limit]]


def get_task(task_id: str) -> Optional[dict]:
    t = _tasks.get(task_id)
    return t.to_dict() if t else None


def _cookie_for(platform: str) -> str:
    """Read platform-specific cookie from env vars."""
    key = {
        "xhs": "XHS_COOKIE_STR",
        "dy": "DOUYIN_COOKIE_STR",
        "ks": "KS_COOKIE_STR",
        "bili": "BILI_COOKIE_STR",
        "wb": "WEIBO_COOKIE_STR",
    }.get(platform)
    return os.getenv(key, "") if key else ""


def _load_root_dotenv(env: dict) -> None:
    """Load root .env values as subprocess defaults without overriding real env."""
    dotenv = PROJECT_ROOT / ".env"
    if not dotenv.exists():
        return
    try:
        lines = dotenv.read_text(encoding="utf-8").splitlines()
    except OSError:
        return
    for raw in lines:
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip()
        if not key or key in env:
            continue
        value = value.strip().strip('"').strip("'")
        env[key] = value


def _configure_mysql_env(env: dict) -> None:
    """Map project DB_* env vars to MediaCrawler's MYSQL_DB_* names."""
    _load_root_dotenv(env)
    env.setdefault("MYSQL_DB_HOST", env.get("DB_HOST", "127.0.0.1"))
    env.setdefault("MYSQL_DB_PORT", env.get("DB_PORT", "3306"))
    env.setdefault("MYSQL_DB_USER", env.get("DB_USER", "root"))
    env.setdefault("MYSQL_DB_NAME", env.get("DB_NAME", "nev_insight"))
    if "MYSQL_DB_PWD" not in env and env.get("DB_PASSWORD"):
        env["MYSQL_DB_PWD"] = env["DB_PASSWORD"]


def _build_env(task: Task) -> dict:
    """Compose env vars for the subprocess."""
    env = os.environ.copy()
    env["BRAND_NAME"] = task.brand
    env["CRAWLER_MAX_NOTES_COUNT"] = str(task.max_notes)
    # KEYWORDS env is fallback; we also pass --keywords on the CLI which takes precedence
    env["KEYWORDS"] = ",".join(task.keywords)
    # 去掉所有代理环境变量，避免 Clash/V2Ray 等 MITM 让 Chromium 报
    # ERR_SSL_VERSION_OR_CIPHER_MISMATCH（尤其是快手对 TLS 校验严格）
    for k in ("HTTP_PROXY", "HTTPS_PROXY", "ALL_PROXY", "NO_PROXY",
              "http_proxy", "https_proxy", "all_proxy", "no_proxy"):
        env.pop(k, None)
    _configure_mysql_env(env)
    return env


def _build_cmd(task: Task) -> list[str]:
    """Compose CLI args for mediacrawler/main.py."""
    effective_login = task.login_type
    cookies = _cookie_for(task.platform)
    if effective_login == "cookie" and not cookies:
        # No env cookie. Fall back to qrcode for first-time login (no stored session) or
        # let MediaCrawler use the persisted browser session (cached login).
        if not _has_browser_session(task.platform):
            task.log_tail.append(
                f"[runner] no cookie + no session → switching to qrcode (will open browser for QR)")
            effective_login = "qrcode"

    # HEADLESS env var: "no" (default for local/Mac so QR can be scanned)
    # set HEADLESS=yes to run with no UI (only useful when cookie is configured)
    headless = "no" if effective_login == "qrcode" else os.getenv("HEADLESS", "no")
    mode = task.mode if task.mode in ("search", "creator") else "search"
    cmd = [
        PYTHON,
        "main.py",
        "--platform", task.platform,
        "--lt", effective_login,
        "--type", mode,
        "--get_comment", "yes" if task.enable_comments else "no",
        "--get_sub_comment", "no",
        "--headless", headless,
        "--save_data_option", "db",
    ]
    if mode == "creator":
        # creator 模式必须传 --creator_id（逗号分隔多账号）
        cmd += ["--creator_id", ",".join(task.creator_ids)]
        # MediaCrawler creator 模式忽略 keywords，但 CLI 仍要给个值
        cmd += ["--keywords", task.brand or "_"]
    else:
        cmd += ["--keywords", ",".join(task.keywords)]
    if cookies and effective_login == "cookie":
        cmd += ["--cookies", cookies]
    return cmd


async def _run_subprocess(task: Task) -> int:
    """单次执行 mediacrawler 子进程，返回 exit_code（-1 表示超时被 kill）。
    填充 task.exit_code / log_tail，但不设置 task.status（由调用方根据语境设定）。
    """
    cmd = _build_cmd(task)
    env = _build_env(task)
    task.log_tail.append(f"[runner] cmd: {' '.join(cmd)}")
    pwd_state = "SET" if env.get("MYSQL_DB_PWD") else "UNSET"
    task.log_tail.append(
        "[runner] mysql env: "
        f"host={env.get('MYSQL_DB_HOST')} port={env.get('MYSQL_DB_PORT')} "
        f"db={env.get('MYSQL_DB_NAME')} user={env.get('MYSQL_DB_USER')} pwd={pwd_state}"
    )

    try:
        proc = await asyncio.create_subprocess_exec(
            *cmd,
            cwd=str(CRAWLER_DIR),
            env=env,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.STDOUT,
        )
    except Exception as e:
        task.error_message = f"failed to spawn subprocess: {e!r}"
        task.exit_code = -1
        return -1

    async def _drain():
        if proc.stdout is None:
            return
        async for raw in proc.stdout:
            try:
                line = raw.decode("utf-8", errors="replace").rstrip()
            except Exception:
                line = repr(raw)
            if line:
                task.log_tail.append(line)

    drain_task = asyncio.create_task(_drain())
    try:
        exit_code = await asyncio.wait_for(proc.wait(), timeout=DEFAULT_TIMEOUT_SEC)
    except asyncio.TimeoutError:
        proc.kill()
        await proc.wait()
        task.error_message = f"timeout after {DEFAULT_TIMEOUT_SEC}s"
        task.exit_code = -1
        await drain_task
        return -1

    await drain_task
    task.exit_code = exit_code
    return exit_code


def _lock_for_platform(platform: str) -> asyncio.Lock:
    lock = _platform_locks.get(platform)
    if lock is None:
        lock = asyncio.Lock()
        _platform_locks[platform] = lock
    return lock


async def _run_subprocess_locked(task: Task) -> int:
    """MediaCrawler uses a persistent browser profile per platform, so tasks for
    the same platform must not launch Chromium concurrently."""
    lock = _lock_for_platform(task.platform)
    if lock.locked():
        task.log_tail.append(f"[runner] waiting for {task.platform} browser profile lock")
    async with lock:
        return await _run_subprocess(task)


def _ensure_login_task(platform: str) -> str:
    """复用进行中的 login 任务，若无则新起一个。返回 login task id。"""
    last_id = _latest_login_task.get(platform)
    if last_id and last_id in _tasks:
        st = _tasks[last_id].status
        if st in (TaskStatus.PENDING, TaskStatus.RUNNING):
            return last_id
    new_id = submit_login(platform)
    _latest_login_task[platform] = new_id
    return new_id


async def _wait_for_login(login_task_id: str, timeout_sec: int) -> bool:
    """阻塞等待登录任务到达终态。
    优先检查日志里是否已出现登录成功标记 —— 即便任务还没正式 SUCCESS，
    我们也可以提前继续，避免被后续 tiny crawl 拖死。"""
    deadline = time.time() + timeout_sec
    login_ok_markers = [
        "login status confirmed",
        "login successful",
        "登录成功",
        "web_session changed",
    ]
    while time.time() < deadline:
        await asyncio.sleep(3)
        t = _tasks.get(login_task_id)
        if not t:
            return False
        if t.status == TaskStatus.SUCCESS:
            return True
        # 早退：登录已成功但 tiny crawl 还在跑，我们不必等它
        log_blob = "\n".join(t.log_tail).lower()
        if any(m in log_blob for m in login_ok_markers):
            return True
        if t.status in (TaskStatus.FAILED, TaskStatus.TIMEOUT):
            return False
    return False


async def _run_task(task: Task) -> None:
    task.status = TaskStatus.RUNNING
    task.started_at = time.time()

    exit_code = await _run_subprocess_locked(task)
    auth_failed = _looks_like_auth_failure(task)
    success = (exit_code == 0) and not auth_failed

    # cookie 模式 + 检测到 cookie 失效 → 触发重新登录 + 等待 + 重试
    is_login_only = (task.brand == "login-only")
    if (not success) and auth_failed and (task.login_type == "cookie") and (not is_login_only):
        login_id = _ensure_login_task(task.platform)
        task.status = TaskStatus.WAITING_LOGIN
        task.log_tail.append(
            f"[runner] cookie 已失效，触发登录任务 {login_id[:8]}，等待最长 {LOGIN_WAIT_SEC}s 让你扫码")
        task.error_message = f"cookie 失效，等待重新登录（最长 {LOGIN_WAIT_SEC}s）..."

        ok = await _wait_for_login(login_id, LOGIN_WAIT_SEC)
        if not ok:
            task.status = TaskStatus.FAILED
            task.error_message = f"重新登录超时（{LOGIN_WAIT_SEC}s 内未完成扫码或登录失败）"
            task.finished_at = time.time()
            return

        # Login 成功 → 重试原任务
        task.log_tail.append(f"[runner] 登录成功，重试原任务 (attempt #2)")
        task.status = TaskStatus.RUNNING
        task.error_message = None
        exit_code = await _run_subprocess_locked(task)
        auth_failed = _looks_like_auth_failure(task)
        success = (exit_code == 0) and not auth_failed

    task.finished_at = time.time()

    # login-only 任务：只要日志里出现登录成功标记就算 SUCCESS（后续 tiny crawl 失败可忽略）
    if is_login_only:
        login_ok_markers = [
            "login status confirmed",       # xhs check_login_state
            "login successful",             # xhs / general
            "登录成功",
            "web_session changed",
        ]
        log_blob = "\n".join(task.log_tail).lower()
        if any(m in log_blob for m in login_ok_markers):
            task.status = TaskStatus.SUCCESS
            task.error_message = None
            task.log_tail.append("[runner] login-only 任务：检测到登录成功标记，标记 SUCCESS（忽略 tiny crawl 结果）")
            return

    if success:
        task.status = TaskStatus.SUCCESS
        return

    # 终态失败：填错误信息
    task.status = TaskStatus.FAILED
    if not task.error_message:
        # 用最后含 error/exception 的日志作为提示
        for line in reversed(task.log_tail):
            if "error" in line.lower() or "exception" in line.lower():
                task.error_message = line[:500]
                break
        if not task.error_message and task.log_tail:
            task.error_message = task.log_tail[-1][:500]
    if auth_failed and task.login_type == "cookie":
        task.error_message = (task.error_message or "") + " · cookie 仍失效，已超过等待时间"


def submit(
    platform: str,
    brand: str,
    keywords: list[str],
    max_notes: int = 10,
    enable_comments: bool = True,
    login_type: str = "cookie",
    mode: str = "search",
    creator_ids: list[str] | None = None,
) -> str:
    """Create and start a task, return its id."""
    task = Task(
        task_id=str(uuid.uuid4()),
        platform=platform,
        brand=brand,
        keywords=keywords,
        max_notes=max_notes,
        enable_comments=enable_comments,
        login_type=login_type,
        mode=mode,
        creator_ids=creator_ids or [],
    )
    _tasks[task.task_id] = task
    asyncio.create_task(_run_task(task))
    return task.task_id


def submit_login(platform: str) -> str:
    """Submit a tiny task that forces qrcode + visible browser, just to refresh login state."""
    keyword = {"xhs": "登录", "dy": "登录", "ks": "登录", "wb": "登录", "bili": "登录"}.get(platform, "登录")
    task = Task(
        task_id=str(uuid.uuid4()),
        platform=platform,
        brand="login-only",
        keywords=[keyword],
        max_notes=1,
        enable_comments=False,
        login_type="qrcode",
    )
    _tasks[task.task_id] = task
    # Best-effort schedule. If no running event loop, log error.
    try:
        asyncio.get_event_loop().create_task(_run_task(task))
    except RuntimeError:
        # In rare cases (called from sync context outside FastAPI), spawn via thread
        import threading
        threading.Thread(target=lambda: asyncio.run(_run_task(task)), daemon=True).start()
    return task.task_id
