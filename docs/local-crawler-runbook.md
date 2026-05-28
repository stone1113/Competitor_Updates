# 本地爬虫运行手册

更新时间：2026-05-27

## 目标

在 Windows 本地直接运行 `crawler-service`，复用本机 MySQL `nev_insight`，抓取微博官号内容并沉淀到竞品动态的数据链路。

## 本地 DB 约定

- MySQL：`127.0.0.1:3306`
- 数据库：`nev_insight`
- 用户：`root`
- 密码：只放在环境变量或项目根目录 `.env`，不要写入代码。

`crawler-service/runner.py` 会读取根目录 `.env`，并自动做这组映射：

- `DB_HOST` -> `MYSQL_DB_HOST`
- `DB_PORT` -> `MYSQL_DB_PORT`
- `DB_NAME` -> `MYSQL_DB_NAME`
- `DB_USER` -> `MYSQL_DB_USER`
- `DB_PASSWORD` -> `MYSQL_DB_PWD`

如果 `.env` 里已经直接配置 `MYSQL_DB_*`，则优先使用 `MYSQL_DB_*`。

## 启动顺序

1. 确认 MySQL 已启动，且 `nev_insight` 里有 MediaCrawler 所需表，例如 `weibo_note`、`weibo_creator`。
2. 确认后端 8090 已启动，且 `CRAWLER_BASE_URL` 指向 `http://localhost:8091` 或可访问的 8091 地址。
3. 启动 crawler-service：

```powershell
cd D:\m-monitor\m-monitor\crawler-service
.\.venv-win\Scripts\python.exe -m uvicorn server:app --host 0.0.0.0 --port 8091
```

4. 在任务日志里确认：

```text
[runner] mysql env: host=127.0.0.1 port=3306 db=nev_insight user=root pwd=SET
```

## 微博登录

微博官号抓取优先复用本地浏览器 profile：

```text
crawler-service/mediacrawler/browser_data/wb_user_data_dir
```

如果 Playwright 窗口验证码无法点击，用普通 Chrome 指向这个用户目录完成手机号登录。登录后访问：

```text
https://m.weibo.cn/api/config
```

看到 `login:true` 后关闭普通 Chrome，再让 crawler-service 使用该 profile。

## 官号抓取链路

1. 触发微博官号抓取：

```powershell
Invoke-RestMethod -Method Post "http://localhost:8090/api/v1/official-accounts/crawl-now?platform=wb"
```

2. 查看 crawler-service 任务：

```powershell
Invoke-RestMethod "http://localhost:8091/tasks?limit=20"
```

3. 把 `weibo_note` 写入竞品动态新闻池：

```powershell
Invoke-RestMethod -Method Post "http://localhost:8090/api/v1/official-accounts/ingest-now"
```

4. 需要卡片事件类型和 OCR 时再执行：

```powershell
Invoke-RestMethod -Method Post "http://localhost:8090/api/v1/collector/extract-events?maxBatches=12"
```

## 排障记录

- 如果报 `Access denied for user 'root'@'localhost'`，先看 8091 任务日志里的 `mysql env`，通常是 `MYSQL_DB_*` 未注入，MediaCrawler 使用了旧默认库。
- 如果任务显示 SUCCESS 但没有新数据，检查微博 `pong/config` 登录态；现在 `core.py` 会在 cookie 失效时抛错，不再假成功。
- 如果同平台多个任务同时启动，runner 会按平台加锁，避免多个 Chromium 抢同一个 profile。
- `weibo_note.create_time` 是秒级时间戳，`web_search_news.add_ts` 是毫秒；进入竞品动态前必须跑 `ingest-now`。
