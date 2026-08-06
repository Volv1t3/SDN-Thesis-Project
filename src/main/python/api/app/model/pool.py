"""Implementa un pool asincrono acotado de clasificadores."""

from __future__ import annotations

import asyncio
import time
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from typing import Generic, Protocol, TypeVar

from app.sdn_mpls_ml_exceptions import InferenceCapacityExceededError

ClassifierT = TypeVar("ClassifierT")


class ClassifierPoolObserver(Protocol):
    """Contrato para publicar eventos y estado del pool sin acoplarlo a Prometheus."""

    def observe_wait(self, *, duration_seconds: float, outcome: str) -> None: ...

    def set_state(self, *, capacity: int, available: int, borrowed: int) -> None: ...

    def record_timeout(self) -> None: ...


class ClassifierPool(Generic[ClassifierT]):
    """Gestiona la cesion exclusiva de clasificadores por solicitud."""

    def __init__(self, classifiers: list[ClassifierT], observer: ClassifierPoolObserver | None = None) -> None:
        """Inicializa el pool con una lista no vacia de clasificadores."""

        if not classifiers:
            raise ValueError("El pool requiere al menos una instancia de clasificador.")

        self._capacity = len(classifiers)
        self._observer = observer
        self._available: asyncio.Queue[ClassifierT] = asyncio.Queue(maxsize=self._capacity)
        for classifier in classifiers:
            self._available.put_nowait(classifier)
        self._publish_state()

    @property
    def capacity(self) -> int:
        """Cantidad total de clasificadores administrados por el pool."""

        return self._capacity

    @property
    def available(self) -> int:
        """Cantidad de clasificadores ociosos."""

        return self._available.qsize()

    @property
    def borrowed(self) -> int:
        """Cantidad de clasificadores actualmente prestados."""

        return self._capacity - self._available.qsize()

    @asynccontextmanager
    async def acquire(self, timeout_seconds: float) -> AsyncIterator[ClassifierT]:
        """Entrega un clasificador disponible o falla por timeout controlado."""

        wait_started = time.perf_counter()
        try:
            classifier = await asyncio.wait_for(self._available.get(), timeout=timeout_seconds)
        except TimeoutError as exc:
            self._observe_wait(time.perf_counter() - wait_started, "timeout")
            if self._observer is not None:
                self._observer.record_timeout()
            self._publish_state()
            raise InferenceCapacityExceededError() from exc
        except asyncio.CancelledError:
            self._observe_wait(time.perf_counter() - wait_started, "cancelled")
            self._publish_state()
            raise

        self._observe_wait(time.perf_counter() - wait_started, "acquired")
        self._publish_state()

        try:
            yield classifier
        finally:
            self._available.put_nowait(classifier)
            self._publish_state()

    def _observe_wait(self, duration_seconds: float, outcome: str) -> None:
        if self._observer is not None:
            self._observer.observe_wait(duration_seconds=duration_seconds, outcome=outcome)

    def _publish_state(self) -> None:
        if self._observer is not None:
            self._observer.set_state(
                capacity=self.capacity,
                available=self.available,
                borrowed=self.borrowed,
            )
