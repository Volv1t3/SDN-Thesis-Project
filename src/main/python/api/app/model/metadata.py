"""Define y valida el contrato estricto de metadata del modelo.

Pasos:
- Declara el orden de features y clases esperadas por la API.
- Valida compatibilidad entre metadata cargada y contrato del runtime.
- Expone un loader seguro desde archivos JSON UTF-8.
"""

from __future__ import annotations

import json
from pathlib import Path

from pydantic import BaseModel, ConfigDict, ValidationError, field_validator, model_validator

from app.messages import Messages
EXPECTED_FEATURE_ORDER = ["eth_type", "ip_proto", "src_port", "dst_port"]
EXPECTED_MODEL_NAME = "sdnflow_xgboost_first_packet"
EXPECTED_CLASS_TO_ID = {
    "DNS": 0,
    "FTP": 1,
    "HTTP": 2,
    "ICMP": 3,
    "NTP": 4,
    "SSH": 5,
    "STREAMING": 6,
}


class ModelMetadata(BaseModel):
    """Representa la metadata compatible con el clasificador soportado.

    Pasos:
    - Valida campos basicos con Pydantic estricto.
    - Comprueba nombre, formato, features y mapeos de clase.

    Notas:
    - Los campos extra del archivo se ignoran para tolerar metadata ampliada.
    """

    model_config = ConfigDict(extra="ignore", strict=True)

    schema_version: str
    model_name: str
    target_name: str
    model_format: str
    feature_order: list[str]
    feature_types: dict[str, str]
    class_to_id: dict[str, int]
    id_to_class: dict[str, str]

    @field_validator("schema_version")
    @classmethod
    def validate_schema_version(cls, value: str) -> str:
        """Verifica la version del schema de metadata.

        Argumentos:
        - value: version declarada en el archivo.

        Retorna:
        - str: la misma version si es valida.

        Excepciones:
        - ValueError: si la version no coincide con `1.0`.
        """

        if value != "1.0":
            raise ValueError(Messages.METADATA_SCHEMA_VERSION)
        return value

    @model_validator(mode="after")
    def validate_contract(self) -> "ModelMetadata":
        """Comprueba el contrato funcional completo de la metadata.

        Pasos:
        - Verifica nombre y formato esperados del modelo.
        - Comprueba el orden y los tipos de las features.
        - Valida cardinalidad y reversibilidad del mapa de clases.

        Retorna:
        - ModelMetadata: la misma instancia validada.

        Excepciones:
        - ValueError: si cualquier parte del contrato es incompatible.
        """

        if self.model_name != EXPECTED_MODEL_NAME:
            raise ValueError(Messages.unexpected_model_name(self.model_name))
        if self.model_format != "xgboost_booster_json":
            raise ValueError(Messages.METADATA_MODEL_FORMAT_INVALID)
        if self.feature_order != EXPECTED_FEATURE_ORDER:
            raise ValueError(Messages.METADATA_FEATURE_ORDER_INVALID)
        if set(self.feature_types) != set(EXPECTED_FEATURE_ORDER):
            raise ValueError(Messages.METADATA_FEATURE_TYPES_INVALID)
        if any(value != "integer" for value in self.feature_types.values()):
            raise ValueError(Messages.METADATA_FEATURE_TYPES_INTEGER)
        if len(self.class_to_id) != len(EXPECTED_CLASS_TO_ID):
            raise ValueError(Messages.METADATA_CLASS_TO_ID_COUNT)
        if len(self.id_to_class) != len(EXPECTED_CLASS_TO_ID):
            raise ValueError(Messages.METADATA_ID_TO_CLASS_COUNT)
        ids = list(self.class_to_id.values())
        if sorted(ids) != list(range(7)):
            raise ValueError(Messages.METADATA_CLASS_IDS_CONTIGUOUS)
        if self.class_to_id != EXPECTED_CLASS_TO_ID:
            raise ValueError(Messages.METADATA_CLASS_TO_ID_CONTRACT)
        expected_reverse = {str(value): key for key, value in EXPECTED_CLASS_TO_ID.items()}
        if self.id_to_class != expected_reverse:
            raise ValueError(Messages.METADATA_ID_TO_CLASS_CONTRACT)
        return self

    @property
    def classes(self) -> list[tuple[int, str]]:
        """Expone las clases ordenadas por identificador ascendente.

        Retorna:
        - list[tuple[int, str]]: pares ordenados de id y nombre de clase.
        """

        return [(class_id, self.id_to_class[str(class_id)]) for class_id in range(len(self.id_to_class))]


def load_metadata(path: str | Path) -> ModelMetadata:
    """Carga metadata desde un archivo JSON UTF-8.

    Pasos:
    - Lee el archivo desde disco.
    - Parsea su contenido como JSON.
    - Valida el payload contra `ModelMetadata`.

    Argumentos:
    - path: ruta del archivo de metadata.

    Retorna:
    - ModelMetadata: metadata validada y compatible.

    Excepciones:
    - ValueError: si el archivo no puede leerse, parsearse o validarse.
    """

    try:
        raw_text = Path(path).read_text(encoding="utf-8")
        payload = json.loads(raw_text)
    except json.JSONDecodeError as exc:
        raise ValueError(Messages.METADATA_FILE_INVALID_JSON) from exc
    except OSError as exc:
        raise ValueError(Messages.metadata_file_read_failed(str(path))) from exc

    try:
        return ModelMetadata.model_validate(payload)
    except ValidationError as exc:
        raise ValueError(Messages.metadata_validation_failed(str(exc))) from exc
