"""
SDN-MPLS-ML Tech Demonstrator
Santiago Arellano 00328370

Endpoint definido para la exposicion de metricas de la aplicacion
"""

from __future__ import annotations

import os

from fastapi import APIRouter, Request, Response
from prometheus_client import CONTENT_TYPE_LATEST, REGISTRY, CollectorRegistry, generate_latest, multiprocess
from starlette.status import HTTP_404_NOT_FOUND

router = APIRouter()


def _registry_for_scrape() -> CollectorRegistry:
    """
    Funcion auxiliar para determinar el registry correcto para scrappear
    segun la configuracion del directorio multiprocess.
    Si PROMETHEUS_MULTIPROC_DIR no esta seteado, se usa el registry default.
    :return:  CollectorRegistry: Registry apropiado para scrappear
    :rtype: CollectorRegistry
    """
    multiprocess_dir = os.getenv("PROMETHEUS_MULTIPROC_DIR", "").strip()
    if not multiprocess_dir:
        return REGISTRY
    registry = CollectorRegistry(support_collectors_without_names=True)
    multiprocess.MultiProcessCollector(registry, path=multiprocess_dir)
    return registry


@router.get("/metrics", include_in_schema=False)
def metrics(request: Request) -> Response:
    """
    Devuelve la exposicion Prometheus de todas las metricas registradas.

    Args:
        request: solicitud FastAPI con acceso a servicios compartidos.

    Retorna:
        Response: Respuesta HTTP con las metricas de Prometheus o 404.
    Notas:
    - Esta ruta esta excluida del schema OpenAPI.
    - El endpoint devuelve 404 si las metricas no estan habilitadas.
    """

    services = getattr(request.app.state, "services", None)
    if services is not None and services.settings is not None and not services.settings.enable_prometheus_metrics:
        return Response(status_code=HTTP_404_NOT_FOUND)
    return Response(
        content=generate_latest(_registry_for_scrape()),
        headers={"Content-Type": CONTENT_TYPE_LATEST},
    )
