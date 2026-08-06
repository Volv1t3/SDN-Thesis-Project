"""Pruebas de la exposicion Prometheus base."""

from __future__ import annotations

from prometheus_client import CONTENT_TYPE_LATEST

from app.sdn_mpls_ml_main import app


def test_metrics_route_exports_baseline_metrics_and_is_hidden_from_openapi(client):
    """El endpoint unico exporta metricas y no forma parte del contrato OpenAPI."""

    response = client.get("/metrics")

    assert response.status_code == 200
    assert response.headers["content-type"] == CONTENT_TYPE_LATEST
    assert response.headers["x-request-id"]
    for metric_name in (
        "sdnflow_worker_info",
        "sdnflow_readiness",
        "sdnflow_classifier_pool_capacity",
        "sdnflow_classifier_pool_available",
        "sdnflow_classifier_pool_borrowed",
    ):
        assert metric_name in response.text

    assert "/metrics" not in app.openapi()["paths"]
    assert "request_id=" not in response.text
    assert "src_port=" not in response.text
