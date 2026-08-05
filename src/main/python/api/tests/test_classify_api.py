"""Prueba el contrato HTTP principal de los endpoints de clasificacion.

Pasos:
- Verifica metadata expuesta por `/api/v1/model`.
- Ejerce clasificaciones correctas e incorrectas.
- Confirma ausencia de endpoints no soportados.
"""

from uuid import UUID


def test_model_endpoint(client):
    """Verifica que el endpoint de modelo exponga metadata utilizable."""

    response = client.get("/api/v1/model")
    assert response.status_code == 200
    assert response.json()["model_name"] == "sdnflow_xgboost_first_packet"
    assert response.headers["X-Request-ID"]


def test_classify_valid_request(client):
    """Confirma que una solicitud valida devuelve prediccion y probabilidades."""

    response = client.post(
        "/api/v1/classify",
        json={
            "packet_features": {
                "eth_type": 2048,
                "ip_proto": 6,
                "src_port": 51514,
                "dst_port": 443,
            }
        },
    )
    assert response.status_code == 200
    body = response.json()
    assert body["request_id"] == response.headers["X-Request-ID"]
    UUID(body["request_id"])
    assert body["prediction"]["class_name"] == "STREAMING"
    assert "processing_time_ms" in body
    assert "orchestration" not in body
    assert set(body["probabilities"]) == {"DNS", "FTP", "HTTP", "ICMP", "NTP", "SSH", "STREAMING"}


def test_classify_not_ready_returns_structured_503(client):
    """Verifica que un estado no listo bloquee clasificacion con diagnostico."""

    client.app.state.services.readiness.ready = False
    client.app.state.services.readiness.initialization_completed = True
    client.app.state.services.readiness.error_component = "inference_service"
    client.app.state.services.readiness.failed_stage = "runtime_model_compatibility"
    client.app.state.services.readiness.failed_check = "synthetic_inference"
    client.app.state.services.readiness.retryable = False
    response = client.post(
        "/api/v1/classify",
        json={
            "packet_features": {
                "eth_type": 2048,
                "ip_proto": 6,
                "src_port": 51514,
                "dst_port": 443,
            },
        },
    )
    assert response.status_code == 503
    body = response.json()
    assert body["request_id"] == response.headers["X-Request-ID"]
    UUID(body["request_id"])
    assert body["error"]["code"] == "MODEL_NOT_READY"
    assert body["error"]["failed_stage"] == "runtime_model_compatibility"
    assert body["error"]["component"] == "inference_service"


def test_classify_invalid_request(client):
    """Comprueba que los errores de schema se normalicen a 422 estructurado."""

    response = client.post(
        "/api/v1/classify",
        json={
            "packet_features": {
                "eth_type": 2048,
                "ip_proto": 1,
                "src_port": 51514,
                "dst_port": 0,
            }
        },
    )
    assert response.status_code == 422
    body = response.json()
    assert body["error"]["code"] == "REQUEST_VALIDATION_FAILED"
    assert body["error"]["message"] == "El cuerpo de la solicitud no cumple con el contrato requerido."
    assert body["error"]["component"] == "request_validation"
    assert body["error"]["failed_stage"] == "request_schema_validation"
    assert body["error"]["failed_check"] == "pydantic_schema"
    assert body["error"]["retryable"] is False
    assert body["request_id"] == response.headers["X-Request-ID"]
    UUID(body["request_id"])


def test_classify_invalid_json_returns_structured_400(client):
    """Comprueba que JSON malformado se distinga de un fallo de schema.

    Pasos:
    - Envia un cuerpo JSON truncado al endpoint de clasificacion.
    - Verifica que la API responda `400 INVALID_JSON`.
    - Confirma que la correlacion HTTP se preserve en header y cuerpo.
    """

    response = client.post(
        "/api/v1/classify",
        content='{"packet_features": ',
        headers={"Content-Type": "application/json"},
    )
    assert response.status_code == 400
    body = response.json()
    assert body["error"]["code"] == "INVALID_JSON"
    assert body["error"]["component"] == "request_validation"
    assert body["error"]["failed_stage"] == "request_body_validation"
    assert body["error"]["failed_check"] == "json_parse"
    assert body["error"]["retryable"] is False
    assert body["request_id"] == response.headers["X-Request-ID"]
    UUID(body["request_id"])


def test_request_id_is_not_accepted_in_body(client):
    """Comprueba que el cuerpo no permita fijar el request ID autoritativo."""

    client_supplied_id = "d996cd46-04e2-47aa-8715-83daa215c65e"
    response = client.post(
        "/api/v1/classify",
        json={
            "request_id": client_supplied_id,
            "packet_features": {
                "eth_type": 2048,
                "ip_proto": 17,
                "src_port": 53000,
                "dst_port": 53,
            },
        },
    )
    assert response.status_code == 422
    body = response.json()
    assert body["request_id"] == response.headers["X-Request-ID"]
    assert body["request_id"] != client_supplied_id
    UUID(body["request_id"])


def test_request_too_large_returns_correlated_413(client):
    """Verifica que el rechazo por tamano preserve correlacion y contrato HTTP."""

    response = client.post(
        "/api/v1/classify",
        json={
            "packet_features": {
                "eth_type": 2048,
                "ip_proto": 17,
                "src_port": 53000,
                "dst_port": 53,
            },
            "padding": "x" * 20000,
        },
    )
    assert response.status_code == 413
    body = response.json()
    assert body["error"]["code"] == "REQUEST_TOO_LARGE"
    assert body["request_id"] == response.headers["X-Request-ID"]
    UUID(body["request_id"])


def test_openapi_generated(client):
    """Valida que la especificacion OpenAPI siga disponible."""

    response = client.get("/openapi.json")
    assert response.status_code == 200
    assert "paths" in response.json()


def test_provisioning_endpoint_is_not_registered(client):
    """Confirma que la API no expone el endpoint de provisionamiento deferido."""

    response = client.post("/api/v1/classify-and-provision")
    assert response.status_code == 404
    body = response.json()
    assert body["error"]["code"] == "HTTP_NOT_FOUND"
    assert body["error"]["component"] == "http_routing"
    assert body["error"]["failed_stage"] == "request_routing"
    assert body["error"]["failed_check"] == "route_resolution"
    assert body["error"]["retryable"] is False
    assert body["request_id"] == response.headers["X-Request-ID"]
    assert "/api/v1/classify-and-provision" not in client.get("/openapi.json").json()["paths"]
