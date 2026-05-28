"""
FastAPI wrapper around MediaCrawler.

Endpoints:
  GET  /health                  -> liveness probe
  POST /crawl                   -> trigger a crawl task
  GET  /tasks                   -> list recent tasks
  GET  /tasks/{task_id}         -> single task status
"""
from __future__ import annotations

from typing import Optional

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

from runner import list_tasks, get_task, submit, submit_login
from autohome_scraper import scrape_many as autohome_scrape_many

VALID_PLATFORMS = {"xhs", "dy", "ks", "bili", "wb"}
VALID_LOGINS = {"cookie", "qrcode", "phone"}

app = FastAPI(title="NEV Crawler Service", version="1.0.0")


class CrawlRequest(BaseModel):
    platform: str = Field(..., description="xhs | dy | ks | bili | wb")
    brand: str = Field(..., description="brand_name written into DB")
    mode: str = Field("search", description="search | creator")
    keywords: list[str] = Field(default_factory=list, description="search 模式必填；creator 模式忽略")
    creator_ids: list[str] = Field(default_factory=list,
                                   description="creator 模式必填：账号 UID 或主页 URL 列表")
    max_notes: int = Field(10, ge=1, le=200)
    enable_comments: bool = True
    login_type: str = Field("cookie", description="cookie | qrcode | phone")


class CrawlResponse(BaseModel):
    task_id: str
    status: str = "PENDING"


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/crawl", response_model=CrawlResponse)
async def trigger(req: CrawlRequest):
    if req.platform not in VALID_PLATFORMS:
        raise HTTPException(400, f"invalid platform: {req.platform}; allowed={sorted(VALID_PLATFORMS)}")
    if req.login_type not in VALID_LOGINS:
        raise HTTPException(400, f"invalid login_type: {req.login_type}; allowed={sorted(VALID_LOGINS)}")
    mode = (req.mode or "search").lower()
    if mode not in ("search", "creator"):
        raise HTTPException(400, f"invalid mode: {req.mode}; allowed=[search, creator]")

    keywords = [k.strip() for k in (req.keywords or []) if k and k.strip()]
    creator_ids = [c.strip() for c in (req.creator_ids or []) if c and c.strip()]

    if mode == "search" and not keywords:
        raise HTTPException(400, "search mode requires keywords")
    if mode == "creator" and not creator_ids:
        raise HTTPException(400, "creator mode requires creator_ids")

    task_id = submit(
        platform=req.platform,
        brand=req.brand,
        keywords=keywords,
        max_notes=req.max_notes,
        enable_comments=req.enable_comments,
        login_type=req.login_type,
        mode=mode,
        creator_ids=creator_ids,
    )
    return CrawlResponse(task_id=task_id)


@app.get("/tasks")
def tasks(limit: int = 50):
    return {"items": list_tasks(limit=limit)}


@app.get("/tasks/{task_id}")
def task_detail(task_id: str):
    t = get_task(task_id)
    if not t:
        raise HTTPException(404, f"task not found: {task_id}")
    return t


class LoginRequest(BaseModel):
    platform: str = Field(..., description="xhs | dy | ks | bili | wb")


@app.post("/login")
async def login(req: LoginRequest):
    if req.platform not in VALID_PLATFORMS:
        raise HTTPException(400, f"invalid platform: {req.platform}")
    task_id = submit_login(req.platform)
    return {"task_id": task_id}


# ===== 汽车之家车型参数抓取 =====

class AutohomeRequest(BaseModel):
    series_ids: list[str] = Field(..., min_length=1, max_length=20,
                                   description="汽车之家 seriesid 列表")


@app.post("/scrape-autohome")
async def scrape_autohome(req: AutohomeRequest):
    """同步抓取（阻塞返回）。8 车型 ~ 30-60 秒，可接受。"""
    sids = [s.strip() for s in req.series_ids if s and s.strip()]
    if not sids:
        raise HTTPException(400, "series_ids cannot be empty")
    items = await autohome_scrape_many(sids)
    return {"items": items}
