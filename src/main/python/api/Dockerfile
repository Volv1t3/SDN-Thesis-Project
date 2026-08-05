FROM python:3.14-slim

WORKDIR /api

RUN groupadd --system appgroup \
    && useradd --system --gid appgroup --create-home appuser

COPY ./requirements.txt ./requirements.txt
RUN pip install --no-cache-dir --requirement requirements.txt

COPY ./app ./app
COPY ./configs ./configs
COPY ./models ./models

RUN chown -R appuser:appgroup /api/app /api/configs /api/models

USER appuser


HEALTHCHECK --interval=30s --timeout=3s --start-period=20s --retries=3 \
    CMD sh -c "python -c \"import os, urllib.request; port = os.getenv('PORT', '8000'); urllib.request.urlopen(f'http://127.0.0.1:{port}/health/live', timeout=2)\""

CMD ["sh", "-c", "uvicorn app.main:app --host ${HOST:-0.0.0.0} --port ${PORT:-8000} --workers 1"]
