"""Pruebas de metricas Prometheus de la segunda fase."""

from __future__ import annotations

from app.observability.classification_metrics import (
    CLASSIFICATION_REQUESTS_TOTAL,
    CLASSIFICATION_RESULTS_TOTAL,
    INFERENCE_DURATION_SECONDS,
    POLICY_FALLBACKS_TOTAL,
    POLICY_SELECTIONS_TOTAL,
    PREDICTION_CONFIDENCE,
    REQUEST_BODY_REJECTIONS_TOTAL,
    REQUEST_ERRORS_TOTAL,
)


def _counter_value(metric, **labels) -> float:
    """Lee el valor actual de un contador etiquetado para comparar deltas."""

    return metric.labels(**labels)._value.get()


def _histogram_count(metric, **labels) -> float:
    """Lee la cantidad actual de observaciones de un histograma etiquetado."""

    metric.labels(**labels)
    count_name = f"{metric._name}_count"
    for family in metric.collect():
        for sample in family.samples:
            if sample.name == count_name and sample.labels == labels:
                return sample.value
    return 0.0


def test_successful_classification_records_result_confidence_and_durations(client):
    """Una clasificacion exitosa publica una sola salida y sus observaciones asociadas."""

    mode = "model"
    class_name = "STREAMING"
    success_before = _counter_value(
        CLASSIFICATION_REQUESTS_TOTAL, classification_mode=mode, outcome="success"
    )
    results_before = _counter_value(
        CLASSIFICATION_RESULTS_TOTAL, classification_mode=mode, class_name=class_name
    )
    confidence_before = _histogram_count(
        PREDICTION_CONFIDENCE, classification_mode=mode, class_name=class_name
    )
    inference_before = _histogram_count(
        INFERENCE_DURATION_SECONDS, classification_mode=mode, outcome="success"
    )

    response = client.post(
        "/api/v1/classify",
        json={
            "packet_features": {"eth_type": 2048, "ip_proto": 6, "src_port": 51514, "dst_port": 443}
        },
    )

    assert response.status_code == 200
    assert (
        _counter_value(CLASSIFICATION_REQUESTS_TOTAL, classification_mode=mode, outcome="success")
        == success_before + 1
    )
    assert (
        _counter_value(
            CLASSIFICATION_RESULTS_TOTAL, classification_mode=mode, class_name=class_name
        )
        == results_before + 1
    )
    assert (
        _histogram_count(PREDICTION_CONFIDENCE, classification_mode=mode, class_name=class_name)
        == confidence_before + 1
    )
    assert (
        _histogram_count(INFERENCE_DURATION_SECONDS, classification_mode=mode, outcome="success")
        == inference_before + 1
    )


def test_unsupported_ether_type_records_rejection_and_controlled_error(client):
    """El rechazo de entrada conserva un unico resultado de clasificacion y error HTTP."""

    mode = "model"
    rejected_before = _counter_value(
        CLASSIFICATION_REQUESTS_TOTAL, classification_mode=mode, outcome="rejected"
    )
    error_before = _counter_value(
        REQUEST_ERRORS_TOTAL,
        error_code="MODEL_ETHERTYPE_UNSUPPORTED",
        component="request_validation",
    )

    response = client.post(
        "/api/v1/classify",
        json={
            "packet_features": {
                "eth_type": 34525,
                "ip_proto": 6,
                "src_port": 51514,
                "dst_port": 443,
            }
        },
    )

    assert response.status_code == 422
    assert (
        _counter_value(CLASSIFICATION_REQUESTS_TOTAL, classification_mode=mode, outcome="rejected")
        == rejected_before + 1
    )
    assert (
        _counter_value(
            REQUEST_ERRORS_TOTAL,
            error_code="MODEL_ETHERTYPE_UNSUPPORTED",
            component="request_validation",
        )
        == error_before + 1
    )


def test_low_confidence_policy_fallback_is_counted_as_success(client):
    """Un fallback de politica conserva el exito de clasificacion y publica su razon."""

    mapper = client.app.state.services.policy_mapper
    assert mapper is not None
    mapper._min_policy_confidence = 0.99
    profile_name = "best_effort_no_tunnel_required"
    selection_before = _counter_value(
        POLICY_SELECTIONS_TOTAL, profile_name=profile_name, fallback="true"
    )
    fallback_before = _counter_value(
        POLICY_FALLBACKS_TOTAL,
        reason="confidence_below_threshold",
        predicted_class="STREAMING",
    )

    response = client.post(
        "/api/v1/classify",
        json={
            "packet_features": {"eth_type": 2048, "ip_proto": 6, "src_port": 51514, "dst_port": 443}
        },
    )

    assert response.status_code == 200
    assert response.json()["policy"]["policy_fallback"] is True
    assert (
        _counter_value(POLICY_SELECTIONS_TOTAL, profile_name=profile_name, fallback="true")
        == selection_before + 1
    )
    assert (
        _counter_value(
            POLICY_FALLBACKS_TOTAL,
            reason="confidence_below_threshold",
            predicted_class="STREAMING",
        )
        == fallback_before + 1
    )


def test_request_size_metrics_distinguish_declared_limit_rejections(client):
    """El middleware publica una razon acotada para rechazos por longitud declarada."""

    from tests.test_request_size_middleware import _invoke_http_app

    rejection_before = _counter_value(
        REQUEST_BODY_REJECTIONS_TOTAL, reason="declared_size_exceeded"
    )
    error_before = _counter_value(
        REQUEST_ERRORS_TOTAL,
        error_code="REQUEST_TOO_LARGE",
        component="request_validation",
    )

    status_code, _, _ = _invoke_http_app(
        client.app,
        method="POST",
        path="/api/v1/classify",
        headers=[(b"host", b"testserver"), (b"content-length", b"20000")],
        body_chunks=[b"{}"],
    )

    assert status_code == 413
    assert (
        _counter_value(REQUEST_BODY_REJECTIONS_TOTAL, reason="declared_size_exceeded")
        == rejection_before + 1
    )
    assert (
        _counter_value(
            REQUEST_ERRORS_TOTAL,
            error_code="REQUEST_TOO_LARGE",
            component="request_validation",
        )
        == error_before + 1
    )
