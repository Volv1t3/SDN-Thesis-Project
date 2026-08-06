"""Prueba el comportamiento basico del pool asincrono de clasificadores."""

from __future__ import annotations

import asyncio

import pytest

from app.model.pool import ClassifierPool
from app.sdn_mpls_ml_exceptions import InferenceCapacityExceededError


@pytest.mark.anyio
async def test_pool_initialization_with_five_instances():
    """El pool expone capacidad y contadores consistentes al inicializar."""

    pool = ClassifierPool([object() for _ in range(5)])
    assert pool.capacity == 5
    assert pool.available == 5
    assert pool.borrowed == 0


@pytest.mark.anyio
async def test_pool_acquire_and_release_updates_invariants():
    """Adquirir y liberar una instancia conserva las invariantes del pool."""

    classifier = object()
    pool = ClassifierPool([classifier])
    async with pool.acquire(timeout_seconds=0.1) as borrowed:
        assert borrowed is classifier
        assert pool.capacity == 1
        assert pool.available == 0
        assert pool.borrowed == 1
    assert pool.available == 1
    assert pool.borrowed == 0


@pytest.mark.anyio
async def test_pool_release_after_exception():
    """La instancia vuelve al pool incluso si el caller falla."""

    pool = ClassifierPool([object()])
    with pytest.raises(RuntimeError):
        async with pool.acquire(timeout_seconds=0.1):
            raise RuntimeError("forced-caller-error")
    assert pool.available == 1
    assert pool.borrowed == 0


@pytest.mark.anyio
async def test_pool_release_after_task_cancellation():
    """La cancelacion posterior a adquirir devuelve la instancia al pool."""

    pool = ClassifierPool([object()])
    acquired = asyncio.Event()
    keep_borrowed = asyncio.Event()

    async def borrow_until_cancelled() -> None:
        async with pool.acquire(timeout_seconds=0.1):
            acquired.set()
            await keep_borrowed.wait()

    task = asyncio.create_task(borrow_until_cancelled())
    await acquired.wait()
    assert pool.borrowed == 1
    task.cancel()
    with pytest.raises(asyncio.CancelledError):
        await task
    assert pool.available == 1
    assert pool.borrowed == 0


@pytest.mark.anyio
async def test_pool_timeout_when_all_instances_are_borrowed():
    """Un acquire sin capacidad disponible falla con el error tipado."""

    pool = ClassifierPool([object()])
    async with pool.acquire(timeout_seconds=0.1):
        with pytest.raises(InferenceCapacityExceededError):
            async with pool.acquire(timeout_seconds=0.01):
                pass


@pytest.mark.anyio
async def test_pool_never_leases_same_instance_twice_concurrently():
    """El mismo objeto no se entrega a dos borrowers al mismo tiempo."""

    first = object()
    second = object()
    pool = ClassifierPool([first, second])
    release_event = asyncio.Event()
    borrowed: list[object] = []

    async def borrow_once():
        async with pool.acquire(timeout_seconds=0.1) as classifier:
            borrowed.append(classifier)
            await release_event.wait()

    tasks = [asyncio.create_task(borrow_once()) for _ in range(2)]
    while len(borrowed) < 2:
        await asyncio.sleep(0)
    assert borrowed[0] is not borrowed[1]
    assert pool.available + pool.borrowed == pool.capacity
    release_event.set()
    await asyncio.gather(*tasks)
