"""Endpoint unico de exposicion Prometheus."""

from __future__ import annotations

import os

from fastapi import APIRouter, Request, Response
from prometheus_client import CONTENT_TYPE_LATEST, REGISTRY, CollectorRegistry, generate_latest, multiprocess
from starlette.status import HTTP_404_NOT_FOUND

router = APIRouter()


def _registry_for_scrape() -> CollectorRegistry:
    multiprocess_dir = os.getenv("PROMETHEUS_MULTIPROC_DIR", "").strip()
    if not multiprocess_dir:
        return REGISTRY
    registry = CollectorRegistry(support_collectors_without_names=True)
    multiprocess.MultiProcessCollector(registry, path=multiprocess_dir)
    return registry


@router.get("/metrics", include_in_schema=False)
def metrics(request: Request) -> Response:
    """Devuelve la exposicion Prometheus de todas las metricas registradas."""

    services = getattr(request.app.state, "services", None)
    if services is not None and services.settings is not None and not services.settings.enable_prometheus_metrics:
        return Response(status_code=HTTP_404_NOT_FOUND)
    return Response(
        content=generate_latest(_registry_for_scrape()),
        headers={"Content-Type": CONTENT_TYPE_LATEST},
    )
