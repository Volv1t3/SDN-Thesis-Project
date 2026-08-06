"""Pruebas del adaptador de metricas en el pool de clasificadores."""

from __future__ import annotations

import pytest

from app.model.sdn_mpls_ml_classifier_pool import ClassifierPool
from app.sdn_mpls_ml_exceptions import InferenceCapacityExceededError


class PoolObserver:
    """Double que registra las publicaciones emitidas por el pool."""

    def __init__(self) -> None:
        self.waits: list[str] = []
        self.states: list[tuple[int, int, int]] = []
        self.timeouts = 0

    def observe_wait(self, *, duration_seconds: float, outcome: str) -> None:
        assert duration_seconds >= 0
        self.waits.append(outcome)

    def set_state(self, *, capacity: int, available: int, borrowed: int) -> None:
        self.states.append((capacity, available, borrowed))

    def record_timeout(self) -> None:
        self.timeouts += 1


@pytest.mark.anyio
async def test_pool_publishes_state_wait_and_timeout_events():
    """El pool publica transiciones y timeout en su propio limite de concurrencia."""

    observer = PoolObserver()
    pool = ClassifierPool([object()], observer=observer)
    assert observer.states[-1] == (1, 1, 0)

    async with pool.acquire(timeout_seconds=0.1):
        assert observer.states[-1] == (1, 0, 1)
        with pytest.raises(InferenceCapacityExceededError):
            async with pool.acquire(timeout_seconds=0.001):
                pass

    assert observer.waits == ["acquired", "timeout"]
    assert observer.timeouts == 1
    assert observer.states[-1] == (1, 1, 0)
