"""
SDN-MPLS-ML Tech Demonstrator
Santiago Arellano 00328370

Archivo que define las funciones publicas del modulo para la carga de las politicas de trafico y
generacion de tuneles. Ademas define internamente la clase que contiene los mecanismos internos para traducir
de una clase de trafico a un tipo de tunel y configuracion de DSCP, TC , Hold Priority, Setup Priority y Bandwidth
"""

from __future__ import annotations
import json
from pathlib import Path
from pydantic import ValidationError
from app.sdn_mpls_ml_messages import Messages
from app.policy.sdn_mpls_ml_policy_validation_models import PolicyFile, TrafficPolicy


def load_policy_file(path: str | Path) -> PolicyFile:
    """
    Carga y valida un archivo de politicas desde disco.

    Pasos:
    - Lee el archivo como texto UTF-8.
    - Parsea el contenido JSON.
    - Valida el payload con `PolicyFile`.

    Args:
        path: ruta del archivo de politicas.

    Returns:
        PolicyFile: politica validada.

    Raises:
        ValueError: si el archivo no puede leerse, parsearse o validarse.
    """

    try:
        #? Carga el archivo como JSON para su validacion posterior
        payload = json.loads(Path(path).read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        raise ValueError(Messages.POLICY_FILE_INVALID_JSON) from exc
    except OSError as exc:
        raise ValueError(Messages.POLICY_FILE_READ_FAILED) from exc

    try:
        #? Valida con un modelo de Pydantic las reglas ingresadas al sistema
        return PolicyFile.model_validate(payload)
    except ValidationError as exc:
        raise ValueError(Messages.policy_file_validation_failed(str(exc))) from exc


class PolicyMapper:
    """
    Resuelve la politica aplicable segun clase y confianza.
    """

    def __init__(self, policy_file: PolicyFile,
                 min_policy_confidence: float | None = None) -> None:
        """
        Inicializa el mapper con politica y umbral opcional de confianza minima en una prediccion para descartar y
        asignar la clase default

        Args:
            policy_file: archivo de politicas ya validado.
            min_policy_confidence: umbral minimo para evitar fallback.
        """

        self._policy_file = policy_file
        self._min_policy_confidence = min_policy_confidence

    def resolve(self, predicted_class: str, confidence: float) -> tuple[TrafficPolicy, bool, str | None]:
        """
        Selecciona una politica segun clase y nivel de confianza.

        Pasos:
        - Evalua si existe umbral configurado.
        - Aplica fallback al perfil por defecto cuando la confianza es baja.
        - Devuelve la politica efectiva y metadatos de fallback.

        Args:
            predicted_class: clase de trafico predicha.
            confidence: confianza asociada a la prediccion.

        Returns:
            tuple[TrafficPolicy, bool, str | None]: politica, bandera de fallback y motivo opcional.
        """

        #? Si la confianza en la prediccion no es nula y es menor al treshold asignamos policita default,
        # si no asignamos politica real
        if self._min_policy_confidence is not None and confidence < self._min_policy_confidence:
            return self._policy_file.default_profile, True, "confidence_below_threshold"

        return self._policy_file.class_policies[predicted_class], False, None

    @property
    def default_policy(self) -> TrafficPolicy:
        """
        Expone el perfil por defecto configurado.

        Returns:
            TrafficPolicy: politica best-effort del archivo cargado.
        """

        return self._policy_file.default_profile
