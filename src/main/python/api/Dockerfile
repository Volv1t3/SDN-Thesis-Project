FROM python:3.14-slim

WORKDIR /api

ARG APP_UID=10001
ARG APP_GID=10001

RUN groupadd --system --gid ${APP_GID} appgroup \
    && useradd --system \
        --uid ${APP_UID} \
        --gid appgroup \
        --create-home \
        appuser

COPY ./requirements.txt ./requirements.txt
RUN pip install --no-cache-dir --requirement requirements.txt

#Copiamos todos los archivos de configuraciones, modelos y la api
COPY ./app ./app
COPY ./configs ./configs
COPY ./models ./models
#Creamos el directorio de logs a donde vamos a crear un symlink directo hacia el host para guardar los logs reales
RUN mkdir -p ./logs

RUN chown -R appuser:appgroup /api/app /api/configs /api/models /api/logs

USER appuser


HEALTHCHECK --interval=30s --timeout=3s --start-period=20s --retries=3 \
    CMD sh -c "python -c \"import os, urllib.request; port = os.getenv('PORT', '8000'); urllib.request.urlopen(f'http://127.0.0.1:{port}/health/live', timeout=2)\""

CMD ["sh", "-c", "uvicorn app.sdn_mpls_ml_main:app --host ${HOST:-0.0.0.0} --port ${PORT:-8000} --workers 1"]
