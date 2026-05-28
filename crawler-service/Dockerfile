# Playwright official Python image — bundles Chromium + system fonts/libs.
# Pinned to mediacrawler/requirements.txt's playwright==1.45.0
FROM mcr.microsoft.com/playwright/python:v1.45.0-jammy

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    TZ=Asia/Shanghai

WORKDIR /app

# Build deps for native wheels (wordcloud, asyncmy on aarch64)
# Playwright image is debian-based; we need gcc/python3-dev
# Node.js: pyexecjs needs a JS runtime to execute douyin/bilibili signing scripts
RUN apt-get update && apt-get install -y --no-install-recommends \
        build-essential \
        python3-dev \
        nodejs \
    && rm -rf /var/lib/apt/lists/*

# 1) Install mediacrawler deps first for layer cache
# (Playwright base image already includes Chromium + most fonts; skipping
# fonts-noto-cjk to avoid flaky apt downloads. Add later if CN rendering needs it.)
COPY mediacrawler/requirements.txt /tmp/mc-requirements.txt
RUN pip install -r /tmp/mc-requirements.txt

# 2) Wrapper deps (mostly already satisfied above; ensures pinned versions)
COPY requirements.txt /tmp/wrap-requirements.txt
RUN pip install -r /tmp/wrap-requirements.txt

# 3) Copy code
COPY mediacrawler /app/mediacrawler
COPY runner.py server.py /app/

# Persisted browser profile (login state)
RUN mkdir -p /app/user_data
VOLUME ["/app/user_data"]

EXPOSE 8091

# Run from /app so server.py imports work; mediacrawler subprocess uses cwd=/app/mediacrawler
CMD ["uvicorn", "server:app", "--host", "0.0.0.0", "--port", "8091", "--workers", "1"]
