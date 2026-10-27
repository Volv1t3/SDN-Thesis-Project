# Python Inference API — API Request Directory

The Python inference API exposes service-state, model-metadata, classification, and Prometheus endpoints. This page 
documents each route and response state independently. The maintained request artifacts are 
stored in `src/main/postman/python-ml-api/`.

Every response carries a server-generated `X-Request-ID` header. Application error bodies and successful
classifications repeat that value as `request_id`. The Python API does not implement Basic authentication, 
so clients should use the configured API base URL and their deployment's access controls.

<show-structure for="chapter" depth="2"/>

## Read the Service Root `GET /` {id="python-api-root-read"}

### Target and request {id="python-api-root-read-request"}

The root resource identifies the HTTP process. It has no request body and remains 
available while the model is initializing or unavailable.

<code-block lang="http">
GET {api-base-url}/ HTTP/1.1
Accept: application/json
</code-block>

### Expected response {id="python-api-root-read-response"}

The route returns `200 OK`.

<code-block lang="json">
{
    "service": "sdnflow-inference-api",
    "version": "1.0.0",
    "status": "running",
    "documentation": "/docs"
}
</code-block>

## Reject an Unsupported Root Method `POST /` {id="python-api-root-method-rejected"}

### Target and request {id="python-api-root-method-rejected-request"}

The root route registers only `GET`. Sending `POST` tests FastAPI's method validation for this resource.

<code-block lang="http">
POST {api-base-url}/ HTTP/1.1
Content-Type: application/json

{}
</code-block>

### Expected response {id="python-api-root-method-rejected-response"}

The application normalizes the framework rejection as `405 Method Not Allowed` with `HTTP_ERROR`.

<code-block lang="json">
{
    "request_id": "eda315c1-b232-4475-9ac4-dd07567878d4",
    "error": {
        "code": "HTTP_ERROR",
        "message": "La solicitud HTTP fallo.",
        "component": null,
        "failed_stage": null,
        "failed_check": null,
        "retryable": null
    }
}
</code-block>

## Read Liveness `GET /health/live` {id="python-api-liveness-read"}

### Target and request {id="python-api-liveness-read-request"}

Liveness confirms that the HTTP process can answer requests. It has no body and does 
not inspect the model, policy, or classifier pool.

<code-block lang="http">
GET {api-base-url}/health/live HTTP/1.1
Accept: application/json
</code-block>

### Expected response {id="python-api-liveness-read-response"}

The route returns `200 OK` whenever the process is live. This route ignores any body that has been passed to it, and 
it only returns the status of the running application

<code-block lang="python" src="../../src/main/python/api/app/schemas/sdn_mpls_ml_health_validation_models.py" include-lines="26-32"/>

<code-block lang="json">
{
    "status": "alive"
}
</code-block>

## Reject an Unsupported Liveness Method `POST /health/live` {id="python-api-liveness-method-rejected"}

### Target and request {id="python-api-liveness-method-rejected-request"}

The liveness route is read-only.

<code-block lang="http">
POST {api-base-url}/health/live HTTP/1.1
Content-Type: application/json

{}
</code-block>

### Expected response {id="python-api-liveness-method-rejected-response"}

The response is `405 Method Not Allowed`.

<code-block lang="json">
{
    "request_id": "9d846506-d67a-4d7a-88f9-2d2a01ef7fc1",
    "error": {
        "code": "HTTP_ERROR",
        "message": "La solicitud HTTP fallo.",
        "component": null,
        "failed_stage": null,
        "failed_check": null,
        "retryable": null
    }
}
</code-block>

## Read Ready Service State `GET /health/ready` {id="python-api-ready-read"}

### Target and request {id="python-api-ready-read-request"}

Readiness reports the cached result of startup validation. It has no request body.

<code-block lang="http">
GET {api-base-url}/health/ready HTTP/1.1
Accept: application/json
</code-block>

### Expected ready response {id="python-api-ready-read-response"}

After all startup checks pass, the route returns `200 OK` and the complete readiness contract.

<code-block lang="python" src="../../src/main/python/api/app/schemas/sdn_mpls_ml_health_validation_models.py" include-lines="47-78"/>

<code-block lang="json">
{
    "status": "ready",
    "ready": true,
    "classification_mode": "model",
    "model_loaded": true,
    "metadata_loaded": true,
    "policy_loaded": true,
    "synthetic_inference_passed": true,
    "model_name": "sdn_mpls_ml_packet_in_classification_model",
    "model_schema_version": "1.0",
    "feature_count": 4,
    "class_count": 7,
    "classifier_pool_ready": true,
    "classifier_pool_size": 5,
    "validated_at_utc": "2026-10-26T16:16:02.616Z"
}
</code-block>

## Read Initializing Service State `GET /health/ready` {id="python-api-ready-initializing"}

### Target and request {id="python-api-ready-initializing-request"}

Send the same bodyless readiness request before startup completes.

<code-block lang="http">
GET {api-base-url}/health/ready HTTP/1.1
Accept: application/json
</code-block>

### Expected initializing response {id="python-api-ready-initializing-response"}

The route returns `503 Service Unavailable` while initialization is running.

<code-block lang="json">
{
  "request_id": "server-generated-request-id",
  "status": "initializing",
  "ready": false,
  "classification_mode": "model",
  "error": null
}
</code-block>

## Read Failed Service State `GET /health/ready` {id="python-api-ready-failed"}

### Target and request {id="python-api-ready-failed-request"}

Send the bodyless readiness request after a startup check has failed, for example when the configured model artifact cannot be loaded.

<code-block lang="http">
GET {api-base-url}/health/ready HTTP/1.1
Accept: application/json
</code-block>

### Expected failed response {id="python-api-ready-failed-response"}

The route returns `503 Service Unavailable` with the cached diagnostic values.

<code-block lang="json">
{
  "request_id": "server-generated-request-id",
  "status": "not_ready",
  "ready": false,
  "classification_mode": "model",
  "error": {
    "code": "startup-error-code",
    "message": "startup validation message",
    "component": "startup-component",
    "failed_stage": "startup-stage",
    "failed_check": "startup-check",
    "retryable": false
  }
}
</code-block>

## Reject an Unsupported Readiness Method `POST /health/ready` {id="python-api-ready-method-rejected"}

### Target and request {id="python-api-ready-method-rejected-request"}

The readiness route accepts only `GET`.

<code-block lang="http">
POST {api-base-url}/health/ready HTTP/1.1
Content-Type: application/json

{}
</code-block>

### Expected response {id="python-api-ready-method-rejected-response"}

The response is `405 Method Not Allowed`.

<code-block lang="json">
{
    "request_id": "a33149b0-d0c7-4ca5-980a-c41e84f79d98",
    "error": {
        "code": "HTTP_ERROR",
        "message": "La solicitud HTTP fallo.",
        "component": null,
        "failed_stage": null,
        "failed_check": null,
        "retryable": null
    }
}
</code-block>

## Read Model Metadata `GET /api/v1/model` {id="python-api-model-read"}

### Target and request {id="python-api-model-read-request"}

This resource publishes the active model metadata. It has no request body.

<code-block lang="http">
GET {api-base-url}/api/v1/model HTTP/1.1
Accept: application/json
</code-block>

### Expected response {id="python-api-model-read-response"}

When readiness and metadata are available, the route returns `200 OK`.

<code-block lang="python" src="../../src/main/python/api/app/schemas/sdn_mpls_ml_inference_validation_models.py" include-lines="141-158"/>

<code-block lang="json">
{
    "model_name": "sdn_mpls_ml_packet_in_classification_model",
    "target_name": "Category",
    "schema_version": "1.0",
    "feature_order": [
        "eth_type",
        "ip_proto",
        "src_port",
        "dst_port"
    ],
    "classes": [
        {
            "id": 0,
            "name": "DNS"
        },
        {
            "id": 1,
            "name": "FTP"
        },
        {
            "id": 2,
            "name": "HTTP"
        },
        {
            "id": 3,
            "name": "ICMP"
        },
        {
            "id": 4,
            "name": "NTP"
        },
        {
            "id": 5,
            "name": "SSH"
        },
        {
            "id": 6,
            "name": "STREAMING"
        }
    ]
}
</code-block>

## Read Unavailable Model Metadata `GET /api/v1/model` {id="python-api-model-not-ready"}

### Target and request {id="python-api-model-not-ready-request"}

Send the metadata request while readiness is false or metadata is absent.

<code-block lang="http">
GET {api-base-url}/api/v1/model HTTP/1.1
Accept: application/json
</code-block>

### Expected response {id="python-api-model-not-ready-response"}

The route returns `503 Service Unavailable`.

<code-block lang="json">
{
  "request_id": "server-generated-request-id",
  "error": {
    "code": "MODEL_NOT_READY",
    "message": "El servicio de inferencia no esta listo.",
    "component": "inference_service",
    "failed_stage": "service_readiness",
    "failed_check": "service_ready",
    "retryable": true
  }
}
</code-block>

## Reject an Unsupported Model Method `POST /api/v1/model` {id="python-api-model-method-rejected"}

### Target and request {id="python-api-model-method-rejected-request"}

The model-metadata resource is read-only.

<code-block lang="http">
POST {api-base-url}/api/v1/model HTTP/1.1
Content-Type: application/json

{}
</code-block>

### Expected response {id="python-api-model-method-rejected-response"}

The response is `405 Method Not Allowed`.

<code-block lang="json">
{
    "request_id": "52e7678a-8640-4a61-8cdf-e521a5878d25",
    "error": {
        "code": "HTTP_ERROR",
        "message": "La solicitud HTTP fallo.",
        "component": null,
        "failed_stage": null,
        "failed_check": null,
        "retryable": null
    }
}
</code-block>

## Classification Request Model `POST /api/v1/classify` {id="python-api-classify-contract"}

### Target and validation {id="python-api-classify-contract-validation"}

Every classification request targets `/api/v1/classify` and contains exactly one `packet_features` object. All four values 
are strict integers. EtherType ranges from `0` to `65535`, IP protocol from `0` to `255`, and 
both ports from `0` to `65535`. TCP (`6`) and UDP (`17`) may use nonzero ports. All other protocols require both 
ports to be zero. In model mode, the classifier accepts IPv4 EtherType `2048`.

<code-block lang="python" src="../../src/main/python/api/app/schemas/sdn_mpls_ml_inference_validation_models.py" include-lines="24-89"/>

### Successful response model {id="python-api-classify-contract-response"}

Every successful classification returns `200 OK` and this model. The predicted class and selected policy depend on the loaded artifacts and request values.

<code-block lang="python" src="../../src/main/python/api/app/schemas/sdn_mpls_ml_inference_validation_models.py" include-lines="88-138"/>

## Classify ICMP Traffic {id="python-api-classify-icmp"}

### Target and request {id="python-api-classify-icmp-request"}

This saved request targets the classifier with ICMP (`ip_proto: 1`). Its zero ports satisfy the non-TCP and non-UDP validation rule.

<code-block lang="http" src="../../src/main/postman/python-ml-api/valid-icmp-classification.http"/>

### Expected response {id="python-api-classify-icmp-response"}

The request is syntactically valid and returns `200 OK` when the service is ready. The model determines the final class.

<code-block lang="json">
{
    "request_id": "3af5775b-522f-42f2-9f22-c0466c423544",
    "model_name": "sdn_mpls_ml_packet_in_classification_model",
    "prediction": {
        "class_id": 3,
        "class_name": "ICMP",
        "confidence": 0.9999462366104126
    },
    "probabilities": {
        "DNS": 1.7227023363375338e-06,
        "FTP": 5.055967267253436e-05,
        "HTTP": 2.792328501755037e-08,
        "ICMP": 0.9999462366104126,
        "NTP": 2.6586852186483156e-07,
        "SSH": 1.1703148175001843e-06,
        "STREAMING": 1.2761749879075523e-08
    },
    "policy": {
        "profile_name": "icmp_tunnel_policy",
        "dscp": 16,
        "mpls_tc": 2,
        "path_constraints": {
            "requested_bandwidth_kbps": 10000,
            "setup_priority": 3,
            "hold_priority": 2
        },
        "policy_fallback": false,
        "policy_fallback_reason": null
    },
    "processing_time_ms": 3.999
}
</code-block>

## Classify DNS Traffic {id="python-api-classify-dns"}

### Target and request {id="python-api-classify-dns-request"}

This saved request targets the classifier with UDP source port `53000` and DNS destination port `53`.

<code-block lang="http" src="../../src/main/postman/python-ml-api/valid-dns-classification.http"/>

### Expected response {id="python-api-classify-dns-response"}

The request satisfies the IPv4 and UDP validation rules. A ready service returns `200 OK` using the successful classification model.

<code-block lang="json">
{
    "request_id": "62b44821-0ad2-4db8-b1f1-d529931cc9c0",
    "model_name": "sdn_mpls_ml_packet_in_classification_model",
    "prediction": {
        "class_id": 0,
        "class_name": "DNS",
        "confidence": 0.9999525547027588
    },
    "probabilities": {
        "DNS": 0.9999525547027588,
        "FTP": 4.953190455125878e-06,
        "HTTP": 1.9045408407691866e-05,
        "ICMP": 1.0326407391403336e-05,
        "NTP": 1.2424477802142064e-07,
        "SSH": 2.6591826554067666e-06,
        "STREAMING": 1.0362988177803345e-05
    },
    "policy": {
        "profile_name": "dns_tunnel_policy",
        "dscp": 18,
        "mpls_tc": 2,
        "path_constraints": {
            "requested_bandwidth_kbps": 10000,
            "setup_priority": 4,
            "hold_priority": 3
        },
        "policy_fallback": false,
        "policy_fallback_reason": null
    },
    "processing_time_ms": 1.125
}
</code-block>

## Classify NTP Traffic {id="python-api-classify-ntp"}

### Target and request {id="python-api-classify-ntp-request"}

This saved request targets the classifier with UDP source port `123` and destination port `56789`.

<code-block lang="http" src="../../src/main/postman/python-ml-api/valid-ntp-classification.http"/>

### Expected response {id="python-api-classify-ntp-response"}

The request satisfies the IPv4 and UDP validation rules. A ready service returns `200 OK` using the successful classification model. 
In this case it is important to note that despite there being an NTP 
class in the dataset, due to the small training sample size this class is often underrepresented in classifications. An example
run of the previous request body returns an HTTP classification instead of an NTP classification. This is not an error 
in the API, instead it is a natural behavior of the model given the sample size from which it could learn about NTP. 

<code-block lang="json">
{
    "request_id": "8622a678-ebec-4489-9e54-5c7bb2572566",
    "model_name": "sdn_mpls_ml_packet_in_classification_model",
    "prediction": {
        "class_id": 2,
        "class_name": "HTTP",
        "confidence": 0.9808483123779297
    },
    "probabilities": {
        "DNS": 0.018454404547810555,
        "FTP": 0.0001288919593207538,
        "HTTP": 0.9808483123779297,
        "ICMP": 0.000126340746646747,
        "NTP": 0.0002875253849197179,
        "SSH": 3.5332428524270654e-05,
        "STREAMING": 0.00011922648991458118
    },
    "policy": {
        "profile_name": "http_tunnel_policy",
        "dscp": 0,
        "mpls_tc": 0,
        "path_constraints": {
            "requested_bandwidth_kbps": 25000,
            "setup_priority": 6,
            "hold_priority": 5
        },
        "policy_fallback": false,
        "policy_fallback_reason": null
    },
    "processing_time_ms": 1.45
}
</code-block>

## Classify FTP Traffic {id="python-api-classify-ftp"}

### Target and request {id="python-api-classify-ftp-request"}

This saved request targets the classifier with TCP source port `21` and destination port `34099`.

<code-block lang="http" src="../../src/main/postman/python-ml-api/valid-ftp-classification.http"/>

### Expected response {id="python-api-classify-ftp-response"}

The request satisfies the IPv4 and TCP validation rules. A ready service returns `200 OK` using the successful classification model. In contrast to NTP, 
FTP did have enough measurements in the training data for it to understand to identify this specific class.

<code-block lang="json">
{
    "request_id": "53c89fc2-d4b6-4811-a2f1-f4f3e11264be",
    "model_name": "sdn_mpls_ml_packet_in_classification_model",
    "prediction": {
        "class_id": 1,
        "class_name": "FTP",
        "confidence": 0.9866169691085815
    },
    "probabilities": {
        "DNS": 0.0022648514714092016,
        "FTP": 0.9866169691085815,
        "HTTP": 0.005068256054073572,
        "ICMP": 0.0008878327789716423,
        "NTP": 0.0003333610075060278,
        "SSH": 0.001181150204502046,
        "STREAMING": 0.003647634293884039
    },
    "policy": {
        "profile_name": "ftp_tunnel_policy",
        "dscp": 10,
        "mpls_tc": 1,
        "path_constraints": {
            "requested_bandwidth_kbps": 25000,
            "setup_priority": 6,
            "hold_priority": 6
        },
        "policy_fallback": false,
        "policy_fallback_reason": null
    },
    "processing_time_ms": 2.524
}
</code-block>

## Classify HTTP(S) Traffic {id="python-api-classify-https"}

### Target and request {id="python-api-classify-https-request"}

This saved request targets the classifier with TCP source port `443` and destination port `51923`.

<code-block lang="http" src="../../src/main/postman/python-ml-api/valid-https-web-traffic-classification.http"/>

### Expected response {id="python-api-classify-https-response"}

The request satisfies the IPv4 and TCP validation rules. A ready service returns `200 OK` using the successful classification model. 
In the training dataset there was no distinction between 
HTTP and HTTPS labels as opposed to other training datasets, as such the model has learned to identify ports `443`, ports
`80`, and `8080` among other configurations to identify HTTP traffic in general.

<code-block lang="json">
{
    "request_id": "d26712e3-6274-433c-9f30-51a4a8206c94",
    "model_name": "sdn_mpls_ml_packet_in_classification_model",
    "prediction": {
        "class_id": 2,
        "class_name": "HTTP",
        "confidence": 0.9999027252197266
    },
    "probabilities": {
        "DNS": 6.7552959990280215e-06,
        "FTP": 4.433978392626159e-05,
        "HTTP": 0.9999027252197266,
        "ICMP": 6.8191366153769195e-06,
        "NTP": 2.5531337541906396e-06,
        "SSH": 1.3470663361658808e-05,
        "STREAMING": 2.330691677343566e-05
    },
    "policy": {
        "profile_name": "http_tunnel_policy",
        "dscp": 0,
        "mpls_tc": 0,
        "path_constraints": {
            "requested_bandwidth_kbps": 25000,
            "setup_priority": 6,
            "hold_priority": 5
        },
        "policy_fallback": false,
        "policy_fallback_reason": null
    },
    "processing_time_ms": 1.4
}
</code-block>

## Classify SSH Traffic {id="python-api-classify-ssh"}

### Target and request {id="python-api-classify-ssh-request"}

This saved request targets the classifier with TCP source port `22` and destination port `55769`.

<code-block lang="http" src="../../src/main/postman/python-ml-api/valid-ssh-traffic-classification.http"/>

### Expected response {id="python-api-classify-ssh-response"}

The request satisfies the IPv4 and TCP validation rules. A ready service returns `200 OK` using the successful classification model.

<code-block lang="json">
{
    "request_id": "501c8763-c634-4092-aa45-ebb8c732c009",
    "model_name": "sdn_mpls_ml_packet_in_classification_model",
    "prediction": {
        "class_id": 5,
        "class_name": "SSH",
        "confidence": 0.9992104768753052
    },
    "probabilities": {
        "DNS": 0.00010103468230227008,
        "FTP": 0.00025071276468224823,
        "HTTP": 0.00025270465994253755,
        "ICMP": 3.8709484215360135e-05,
        "NTP": 1.4493122762360144e-05,
        "SSH": 0.9992104768753052,
        "STREAMING": 0.0001318494905717671
    },
    "policy": {
        "profile_name": "ssh_tunnel_policy",
        "dscp": 26,
        "mpls_tc": 3,
        "path_constraints": {
            "requested_bandwidth_kbps": 10000,
            "setup_priority": 2,
            "hold_priority": 1
        },
        "policy_fallback": false,
        "policy_fallback_reason": null
    },
    "processing_time_ms": 2.287
}
</code-block>

## Classify Streaming Traffic {id="python-api-classify-streaming"}

### Target and request {id="python-api-classify-streaming-request"}

This saved request targets the classifier with UDP source port `55000` and destination port `65000`.

<code-block lang="http" src="../../src/main/postman/python-ml-api/valid-streaming-classification.http"/>

### Expected response {id="python-api-classify-streaming-response"}

The request satisfies the IPv4 and UDP validation rules. A ready service returns `200 OK` using the successful classification model.

<code-block lang="json">
{
    "request_id": "e21063ff-e9fe-47fa-9476-a76fae37cec0",
    "model_name": "sdn_mpls_ml_packet_in_classification_model",
    "prediction": {
        "class_id": 6,
        "class_name": "STREAMING",
        "confidence": 0.891631543636322
    },
    "probabilities": {
        "DNS": 0.02693198435008526,
        "FTP": 0.0021278264466673136,
        "HTTP": 0.07875388115644455,
        "ICMP": 0.0002386998967267573,
        "NTP": 3.208658017683774e-05,
        "SSH": 0.0002839092630892992,
        "STREAMING": 0.891631543636322
    },
    "policy": {
        "profile_name": "streaming_tunnel_policy",
        "dscp": 34,
        "mpls_tc": 4,
        "path_constraints": {
            "requested_bandwidth_kbps": 75000,
            "setup_priority": 1,
            "hold_priority": 0
        },
        "policy_fallback": false,
        "policy_fallback_reason": null
    },
    "processing_time_ms": 0.993
}
</code-block>

## Reject a Client-Supplied Request Identifier {id="python-api-classify-request-id-rejected"}

### Target and request {id="python-api-classify-request-id-rejected-request"}

This saved request adds `request_id` to a body that accepts only `packet_features`.

<code-block lang="http" src="../../src/main/postman/python-ml-api/rejected-body-because-client-sends-forbidden-request-id.http"/>

### Expected response {id="python-api-classify-request-id-rejected-response"}

The strict model rejects the extra field with `422 Unprocessable Entity`.

<code-block lang="json">
{
    "request_id": "248ebbee-856a-4c2f-ae02-b06a56c71d39",
    "error": {
        "code": "REQUEST_VALIDATION_FAILED",
        "message": "El cuerpo de la solicitud no cumple con el contrato requerido.",
        "component": "request_validation",
        "failed_stage": "request_schema_validation",
        "failed_check": "pydantic_schema",
        "retryable": false
    },
    "details": [
        {
            "type": "extra_forbidden",
            "loc": [
                "body",
                "request_id"
            ],
            "msg": "Extra inputs are not permitted",
            "input": "11111111-1111-1111-1111-111111111111"
        }
    ]
}
</code-block>

## Reject a Non-IPv4 EtherType {id="python-api-classify-ethertype-rejected"}

### Target and request {id="python-api-classify-ethertype-rejected-request"}

This saved request uses EtherType `34525`. The body has valid integer types, but model-mode input validation accepts only IPv4 EtherType `2048`.

<code-block lang="http" src="../../src/main/postman/python-ml-api/rejected-non-ipv4-ethertype.http"/>

### Expected response {id="python-api-classify-ethertype-rejected-response"}

The route returns `422 Unprocessable Entity` after schema validation and before model inference.

<code-block lang="json">
{
    "request_id": "d72085b0-001e-4ddd-a0a2-ded3e85320b4",
    "error": {
        "code": "MODEL_ETHERTYPE_UNSUPPORTED",
        "message": "El modelo cargado solo admite trafico IPv4 con EtherType 2048.",
        "component": "request_validation",
        "failed_stage": "model_input_validation",
        "failed_check": "model_supported_eth_type",
        "retryable": false
    }
}
</code-block>

## Reject a Missing Packet Feature Object {id="python-api-classify-missing-features"}

### Target and request {id="python-api-classify-missing-features-request"}

An empty object does not satisfy the required `packet_features` member.

<code-block lang="http">
POST {api-base-url}/api/v1/classify HTTP/1.1
Content-Type: application/json

{}
</code-block>

### Expected response {id="python-api-classify-missing-features-response"}

The route returns `422 Unprocessable Entity`.

<code-block lang="json">
{
    "request_id": "e2daf176-9521-4bfa-ba17-d33bb3e4d4de",
    "error": {
        "code": "REQUEST_VALIDATION_FAILED",
        "message": "El cuerpo de la solicitud no cumple con el contrato requerido.",
        "component": "request_validation",
        "failed_stage": "request_schema_validation",
        "failed_check": "pydantic_schema",
        "retryable": false
    },
    "details": [
        {
            "type": "missing",
            "loc": [
                "body",
                "packet_features"
            ],
            "msg": "Field required",
            "input": {}
        }
    ]
}
</code-block>

## Reject Nonzero ICMP Ports {id="python-api-classify-icmp-ports"}

### Target and request {id="python-api-classify-icmp-ports-request"}

ICMP does not use TCP or UDP ports. This body violates the cross-field validator.

<code-block lang="http">
POST {api-base-url}/api/v1/classify HTTP/1.1
Content-Type: application/json

{
    "packet_features":{
        "eth_type":2048,
        "ip_proto":1,
        "src_port":80,
        "dst_port":0
    }
}
</code-block>

### Expected response {id="python-api-classify-icmp-ports-response"}

The route returns `422 Unprocessable Entity`.

<code-block lang="json">
{
    "request_id": "server-generated-request-id",
    "error": {
        "code": "REQUEST_VALIDATION_FAILED",
        "message": "El cuerpo de la solicitud no cumple con el contrato requerido.",
        "component": "request_validation",
        "failed_stage": "request_schema_validation",
        "failed_check": "pydantic_schema",
        "retryable": false
    }
}
</code-block>

## Reject Invalid Feature Types and Ranges {id="python-api-classify-types-ranges"}

### Target and request {id="python-api-classify-types-ranges-request"}

The schema requires strict integers and the inclusive port range ends at `65535`.

<code-block lang="http">
POST {api-base-url}/api/v1/classify HTTP/1.1
Content-Type: application/json

{
    "packet_features":{
        "eth_type":"2048",
        "ip_proto":6,
        "src_port":0,
        "dst_port":70000
    }
}
</code-block>

### Expected response {id="python-api-classify-types-ranges-response"}

The route returns `422 Unprocessable Entity`.

<code-block lang="json">
{
    "request_id": "af6758b7-9eba-45a9-bb0c-9097f8f6a6bc",
    "error": {
        "code": "REQUEST_VALIDATION_FAILED",
        "message": "El cuerpo de la solicitud no cumple con el contrato requerido.",
        "component": "request_validation",
        "failed_stage": "request_schema_validation",
        "failed_check": "pydantic_schema",
        "retryable": false
    },
    "details": [
        {
            "type": "int_type",
            "loc": [
                "body",
                "packet_features",
                "eth_type"
            ],
            "msg": "Input should be a valid integer",
            "input": "2048"
        },
        {
            "type": "less_than_equal",
            "loc": [
                "body",
                "packet_features",
                "dst_port"
            ],
            "msg": "Input should be less than or equal to 65535",
            "input": 70000,
            "ctx": {
                "le": 65535
            }
        }
    ]
}
</code-block>

## Reject Malformed Classification JSON {id="python-api-classify-malformed-json"}

### Target and request {id="python-api-classify-malformed-json-request"}

The request uses the classification path and JSON media type but provides an incomplete JSON document.

<code-block lang="http">
POST {api-base-url}/api/v1/classify HTTP/1.1
Content-Type: application/json

{"packet_features":
</code-block>

### Expected response {id="python-api-classify-malformed-json-response"}

The route returns `400 Bad Request`.

<code-block lang="json">
{
    "request_id": "c6fd8b59-7ec0-41b2-9ad9-f3f177e83281",
    "error": {
        "code": "INVALID_JSON",
        "message": "El cuerpo de la solicitud no contiene JSON valido.",
        "component": "request_validation",
        "failed_stage": "request_body_validation",
        "failed_check": "json_parse",
        "retryable": false
    },
    "details": [
        {
            "type": "json_invalid",
            "loc": [
                "body",
                25
            ],
            "msg": "JSON decode error",
            "input": {},
            "ctx": {
                "error": "Expecting value"
            }
        }
    ]
}
</code-block>

## Reject an Invalid Content-Length Header {id="python-api-classify-invalid-content-length"}

### Target and request {id="python-api-classify-invalid-content-length-request"}

The request-size middleware validates `Content-Length` before the route parses the body.

<code-block lang="http">
POST {api-base-url}/api/v1/classify HTTP/1.1
Content-Type: application/json
Content-Length: invalid

{}
</code-block>

### Expected response {id="python-api-classify-invalid-content-length-response"}

The middleware returns `400 Bad Request`.

<code-block lang="json">
{
    "request_id": "server-generated-request-id",
    "error": {
        "code": "INVALID_CONTENT_LENGTH",
        "message": "El encabezado Content-Length no es valido.",
        "component": "request_validation",
        "failed_stage": "request_body_validation",
        "failed_check": "content_length_header",
        "retryable": false
    }
}
</code-block>

## Reject an Oversized Classification Body {id="python-api-classify-too-large"}

### Target and request {id="python-api-classify-too-large-request"}

Use a body larger than the deployment's configured maximum body size. The same result applies when a streamed body exceeds the limit after a valid or absent `Content-Length` header.

<code-block lang="http">
POST {api-base-url}/api/v1/classify HTTP/1.1
Content-Type: application/json
Content-Length: configured-limit-plus-one

body-larger-than-the-configured-limit
</code-block>

### Expected response {id="python-api-classify-too-large-response"}

The middleware returns `413 Payload Too Large`.

<code-block lang="json">
{
  "request_id": "server-generated-request-id",
  "error": {
    "code": "REQUEST_TOO_LARGE",
    "message": "El cuerpo de la solicitud supera el tamano maximo configurado.",
    "component": "request_validation",
    "failed_stage": "request_body_validation",
    "failed_check": "maximum_body_size",
    "retryable": false
  }
}
</code-block>

## Read a Classifier That Is Not Ready {id="python-api-classify-not-ready"}

### Target and request {id="python-api-classify-not-ready-request"}

Send an otherwise valid packet while readiness is false.

<code-block lang="http">
POST {api-base-url}/api/v1/classify HTTP/1.1
Content-Type: application/json

{
    "packet_features": {
        "eth_type":2048,
        "ip_proto":6,
        "src_port":51514,
        "dst_port":443
    }
}
</code-block>

### Expected response {id="python-api-classify-not-ready-response"}

The route returns `503 Service Unavailable`.

<code-block lang="json">
{
  "request_id": "server-generated-request-id",
  "error": {
    "code": "MODEL_NOT_READY",
    "message": "El servicio de inferencia no esta listo.",
    "component": "inference_service",
    "failed_stage": "service_readiness",
    "failed_check": "service_ready",
    "retryable": true
  }
}
</code-block>

## Read an Exhausted Classifier Pool {id="python-api-classify-capacity"}

### Target and request {id="python-api-classify-capacity-request"}

Send an otherwise valid packet after all classifier instances remain borrowed beyond `REQUEST_TIMEOUT_SECONDS`.

<code-block lang="http">
POST {api-base-url}/api/v1/classify HTTP/1.1
Content-Type: application/json

{
    "packet_features": {
        "eth_type":2048,
        "ip_proto":6,
        "src_port":51514,
        "dst_port":443
    }
}
</code-block>

### Expected response {id="python-api-classify-capacity-response"}

The route returns `503 Service Unavailable`.

<code-block lang="json">
{
  "request_id": "server-generated-request-id",
  "error": {
    "code": "INFERENCE_CAPACITY_EXCEEDED",
    "message": "No se obtuvo una instancia de clasificador dentro del tiempo permitido.",
    "component": "inference_capacity",
    "failed_stage": "classifier_acquisition",
    "failed_check": "classifier_available_before_timeout",
    "retryable": true
  }
}
</code-block>

## Handle a Model Inference Failure {id="python-api-classify-inference-failure"}

### Target and request {id="python-api-classify-inference-failure-request"}

This valid request produces this state only if model prediction raises the typed inference failure.

<code-block lang="http">
POST {api-base-url}/api/v1/classify HTTP/1.1
Content-Type: application/json

{
    "packet_features": {
        "eth_type":2048,
        "ip_proto":6,
        "src_port":51514,
        "dst_port":443
    }
}
</code-block>

### Expected response {id="python-api-classify-inference-failure-response"}

The route returns `500 Internal Server Error`.

<code-block lang="json">
{
    "request_id": "server-generated-request-id",
    "error": {
        "code": "MODEL_INFERENCE_FAILED",
        "message": "No fue posible determinar la categoria del trafico.",
        "component": "inference_runtime",
        "failed_stage": "request_inference",
        "failed_check": "model_predict",
        "retryable": true
    }
}
</code-block>

## Handle an Invalid Model Output {id="python-api-classify-output-invalid"}

### Target and request {id="python-api-classify-output-invalid-request"}

This valid request produces this state only if the model returns output that violates the prediction contract.

<code-block lang="http">
POST {api-base-url}/api/v1/classify HTTP/1.1
Content-Type: application/json

{
    "packet_features":{
        "eth_type":2048,
        "ip_proto":6,
        "src_port":51514,
        "dst_port":443
    }
}
</code-block>

### Expected response {id="python-api-classify-output-invalid-response"}

The route returns `500 Internal Server Error`.

<code-block lang="json">
{
    "request_id": "server-generated-request-id",
    "error": {
        "code": "MODEL_OUTPUT_INVALID",
        "message": "La salida producida por el modelo no cumple con el contrato requerido.",
        "component": "inference_runtime",
        "failed_stage": "model_output_validation",
        "failed_check": "prediction_output_contract",
        "retryable": false
    }
}
</code-block>

## Handle a Policy Mapping Failure {id="python-api-classify-policy-failure"}

### Target and request {id="python-api-classify-policy-failure-request"}

This valid request produces this state only if policy resolution fails after a prediction is obtained.

<code-block lang="http">
POST {api-base-url}/api/v1/classify HTTP/1.1
Content-Type: application/json

{
    "packet_features":{
        "eth_type":2048,
        "ip_proto":6,
        "src_port":51514,
        "dst_port":443
    }
}
</code-block>

### Expected response {id="python-api-classify-policy-failure-response"}

The route returns `500 Internal Server Error`.

<code-block lang="json">
{
    "request_id": "server-generated-request-id",
    "error": {
        "code": "POLICY_MAPPING_FAILED",
        "message": "No fue posible resolver la politica de trafico.",
        "component": "policy_mapper",
        "failed_stage": "policy_resolution",
        "failed_check": "class_policy_resolution",
        "retryable": false
    }
}
</code-block>

## Handle an Unexpected Classification Failure {id="python-api-classify-internal-error"}

### Target and request {id="python-api-classify-internal-error-request"}

This valid request produces this state only when an unhandled exception escapes the application flow.

<code-block lang="http">
POST {api-base-url}/api/v1/classify HTTP/1.1
Content-Type: application/json

{
    "packet_features":{
        "eth_type":2048,
        "ip_proto":6,
        "src_port":51514,
        "dst_port":443
    }
}
</code-block>

### Expected response {id="python-api-classify-internal-error-response"}

The application returns `500 Internal Server Error` without a stack trace.

<code-block lang="json">
{
    "request_id": "server-generated-request-id",
    "error": {
        "code": "INTERNAL_ERROR",
        "message": "Ocurrio un error interno inesperado.",
        "component": "application",
        "failed_stage": "request_processing",
        "failed_check": "unhandled_exception",
        "retryable": true
    }
}
</code-block>

## Reject an Unsupported Classification Method `GET /api/v1/classify` {id="python-api-classify-method-rejected"}

### Target and request {id="python-api-classify-method-rejected-request"}

The classifier accepts only `POST`.

<code-block lang="http">
GET {api-base-url}/api/v1/classify HTTP/1.1
Accept: application/json
</code-block>

### Expected response {id="python-api-classify-method-rejected-response"}

The response is `405 Method Not Allowed`.

<code-block lang="json">
{
    "request_id": "server-generated-request-id",
    "error": {
        "code": "HTTP_ERROR",
        "message": "La solicitud HTTP fallo."
    }
}
</code-block>

## Scrape Enabled Prometheus Metrics `GET /metrics` {id="python-api-metrics-enabled"}

### Target and request {id="python-api-metrics-enabled-request"}

The metrics route has no request body and is excluded from the OpenAPI schema. This saved request targets the literal `/metrics` route.

<code-block lang="http" src="../../src/main/postman/python-ml-api/new-request.http"/>

### Expected response {id="python-api-metrics-enabled-response"}

When metrics are enabled, the route returns `200 OK` with Prometheus text exposition. Values reflect the worker and current traffic. The response has no request identifier or packet-port labels.

<code-block lang="text">
# HELP sdn_mpls_ml_api_obs_readiness Estado de readiness del servicio.
# TYPE sdn_mpls_ml_api_obs_readiness gauge
sdn_mpls_ml_api_obs_readiness{classification_mode="model"} 1.0
# TYPE sdn_mpls_ml_api_obs_classification_requests_total counter
sdn_mpls_ml_api_obs_classification_requests_total{classification_mode="model",outcome="success"} 12.0
# TYPE sdn_mpls_ml_api_obs_classification_duration_seconds histogram
sdn_mpls_ml_api_obs_classification_duration_seconds_bucket{classification_mode="model",outcome="success",le="0.1"} 12.0
sdn_mpls_ml_api_obs_classification_duration_seconds_count{classification_mode="model",outcome="success"} 12.0
sdn_mpls_ml_api_obs_classification_duration_seconds_sum{classification_mode="model",outcome="success"} 0.42
</code-block>

## Read Disabled Prometheus Metrics `GET /metrics` {id="python-api-metrics-disabled"}

### Target and request {id="python-api-metrics-disabled-request"}

Send the bodyless metrics request with `ENABLE_PROMETHEUS_METRICS=false` in the running application configuration.

<code-block lang="http">
GET {api-base-url}/metrics HTTP/1.1
Accept: text/plain
</code-block>

### Expected response {id="python-api-metrics-disabled-response"}

The route returns an empty `404 Not Found` response body.

## Reject an Unsupported Metrics Method `POST /metrics` {id="python-api-metrics-method-rejected"}

### Target and request {id="python-api-metrics-method-rejected-request"}

The metrics route accepts only `GET`.

<code-block lang="http">
POST {api-base-url}/metrics HTTP/1.1
Content-Type: application/json

{}
</code-block>

### Expected response {id="python-api-metrics-method-rejected-response"}

The response is `405 Method Not Allowed`.

<code-block lang="json">
{
    "request_id": "server-generated-request-id",
    "error": {
        "code": "HTTP_ERROR",
        "message": "La solicitud HTTP fallo."
    }
}
</code-block>

## Reject a Configured but Unregistered Metrics Path {id="python-api-metrics-path-rejected"}

### Target and request {id="python-api-metrics-path-rejected-request"}

`METRICS_PATH` is validated by configuration, but the router exposes only the literal `/metrics` path. A configured alternative does not create another route.

<code-block lang="http">
GET {api-base-url}/configured-metrics-path HTTP/1.1
Accept: text/plain
</code-block>

### Expected response {id="python-api-metrics-path-rejected-response"}

The application returns `404 Not Found`.

<code-block lang="json">
{
    "request_id": "server-generated-request-id",
    "error": {
        "code": "HTTP_NOT_FOUND",
        "message": "No se encontro la ruta solicitada.",
        "component": "http_routing",
        "failed_stage": "request_routing",
        "failed_check": "route_resolution",
        "retryable": false
    }
}
</code-block>

## Reject the Historical Provisioning Path `POST /api/v1/classify-and-provision` {id="python-api-provisioning-path-rejected"}

### Target and request {id="python-api-provisioning-path-rejected-request"}

The inference API classifies traffic and returns a policy representation. It does not provision OpenDaylight devices, so this historical route is not registered.

<code-block lang="http">
POST {api-base-url}/api/v1/classify-and-provision HTTP/1.1
Content-Type: application/json

{"packet_features":{
    "eth_type":2048,
    "ip_proto":6,
    "src_port":51514,
    "dst_port":443
}}
</code-block>

### Expected response {id="python-api-provisioning-path-rejected-response"}

The application returns `404 Not Found`.

<code-block lang="json">
{
    "request_id": "server-generated-request-id",
    "error": {
        "code": "HTTP_NOT_FOUND",
        "message": "No se encontro la ruta solicitada.",
        "component": "http_routing",
        "failed_stage": "request_routing",
        "failed_check": "route_resolution",
        "retryable": false
    }
}
</code-block>

## Reject an Unknown API Path `GET /api/v1/unknown` {id="python-api-unknown-path-rejected"}

### Target and request {id="python-api-unknown-path-rejected-request"}

For any known routing path the system responds with a `404 Not Found` error, passing the packet only through middleware
validations and not computing anything against the system.

<code-block lang="http">
GET {api-base-url}/api/v1/pcep HTTP/1.1
Accept: application/json
</code-block>

### Expected response {id="python-api-unknown-path-rejected-response"}

The application returns `404 Not Found`.

<code-block lang="json">
{
    "request_id": "fb373ed2-99d8-4368-ba0c-b2ee0deeff40",
    "error": {
        "code": "HTTP_NOT_FOUND",
        "message": "No se encontro la ruta solicitada.",
        "component": "http_routing",
        "failed_stage": "request_routing",
        "failed_check": "route_resolution",
        "retryable": false
    }
}
</code-block>

## Reject an Unknown API Path `POST /api/v1/pcep` {id="python-api-pcep-post-path-rejected"}

Similar to the previous section, unregistered paths return an `HTTP_NOT_FOUND` response with an associated `404 Not Found` 
HTTP error code. The request body is ignored as the request is failed during route resolution. Previous length validations 
and other middleware layers are attached.

### Target and request {id="python-api-pcep-path-post-rejected"}

<code-block lang="http">
POST {api-base-url}/api/v1/pcep HTTP/1.1
Content-Type: application/json

{
    "packet_features":
        {
            "eth_type":2048,
            "ip_proto":6,
            "src_port":51514,
            "dst_port":443
        }
}
</code-block>

### Expected response {id="python-api-unknown-path-rejected-response-for-post"}

<code-block lang='JSON'>
{
    "request_id": "ec4cf73a-0c1d-4e47-a7b3-de5c17eea21c",
    "error": {
        "code": "HTTP_NOT_FOUND",
        "message": "No se encontro la ruta solicitada.",
        "component": "http_routing",
        "failed_stage": "request_routing",
        "failed_check": "route_resolution",
        "retryable": false
    }
}
</code-block>