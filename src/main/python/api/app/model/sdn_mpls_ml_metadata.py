"""
SDN-MPLS-ML Tech Demonstrator
Santiago Arellano 00328370

Archivo que define el contenido esperado y la validacion concreta del archivo de metadata del modelo
de XGBoost que tiene que ser proveido al sistema para la ejecucion de la clasificacion de trafico.

Pasos:
- Declara el orden de features y clases esperadas por la API.
- Valida compatibilidad entre metadata cargada y contrato del runtime.
- Expone un loader seguro desde archivos JSON UTF-8.
"""

from __future__ import annotations

from pydantic import BaseModel, ConfigDict, field_validator, model_validator

from app.sdn_mpls_ml_messages import Messages
#? El orden esperado de parametros siempre es este, si es alreves no clasifica correctamente
EXPECTED_FEATURE_ORDER = ["eth_type", "ip_proto", "src_port", "dst_port"]

#? El nombre del modelo configurado es este
EXPECTED_MODEL_NAME = "sdn_mpls_ml_packet_in_classification_model"
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
    """
    Clase que representa los metadatos del modelo de XGBoost para la clasificacion de trafico, al ser una extension
    de una clase de Pydantic, esta clase implementa mecanismos de validacion internos y tipado para la revision
    de todos los parametros del modelo

    Pasos:
    - Valida campos basicos con Pydantic estricto.
    - Comprueba nombre, formato, features y mapeos de clase.

    Notes:
        Los campos extra del archivo se ignoran para tolerar metadata ampliada.
    """

    #? Definimos el comportamiento de validacion estricta, especificamente que ignore extras pero que los campos
    #? definidos en el modelo tengan que estar registrados, asi como desactiva el type coercion para los tipos del
    #? JSON ingresado, lo que resulta en una validacion mas limpia
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
        """
        Verifica la version del schema de metadata.

        Args:
            value: version declarada en el archivo.

        Returns:
            str: la misma version si es valida.

        Raises:
            ValueError: si la version no coincide con `1.0`.
        """

        if value != "1.0":
            raise ValueError(Messages.METADATA_SCHEMA_VERSION)
        return value

    @model_validator(mode="after")
    def validate_contract(self) -> "ModelMetadata":
        """
        Comprueba el contrato funcional completo de la metadata.

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

        Returns:
            list[tuple[int, str]]: pares ordenados de id y nombre de clase.
        """

        return [(class_id, self.id_to_class[str(class_id)]) for class_id in range(len(self.id_to_class))]
