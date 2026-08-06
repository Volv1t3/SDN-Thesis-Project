"""
SDN-MPLS-ML Tech Demonstrator
Santiago Arellano 00328370

Clase que define la funcionalidad completa de la Pool de Clasificadores de tipo ClassifierT que puede ser
implementado mediante pools. La clase describe una implementacion generica de una pool asyncrona en donde
podemos registrar una cierta cantidad de elementos de una clase generica ClassifierT, que se manejan de manera igual
con todos los protocolos de la clase.

La idea de esta clase no es definir una pool especifica, sino que se configura dentro de sdn_mpls_ml_dependencies.py
con las instancias generadas sea de clasificadores reales Classifiers o DeterministicClassifiers dependiendo del
modelo de trabajo de la API.
"""

from __future__ import annotations

import asyncio
import time
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from typing import Generic, Protocol, TypeVar

from app.sdn_mpls_ml_exceptions import InferenceCapacityExceededError

#? Tipo abstracto para la implemetacion de toda la ClassifierPool. Al no tener un tipo asignado
#? la pool se considera completamente generica y puede usar cualquier implementacion de Clasificadores. Mientras que
#? afuera, los clasificadores responden al Prototipo de TrafficClassifier.
ClassifierT = TypeVar("ClassifierT")


class ClassifierPoolObserver(Protocol):
    """
    Definicion de la interface correspondiente a las instancias de ClassifierPool que contienen adicionalmente un
    observer. Este patron nos permiten asociar a una ClassifierPool el comportamiento de un observer al asegurar
    estado mediante las metricas de grafana.

    En este caso, la clase ClassifierPool termina teniendo sus propios metodos basados en un tipado
    generico ClassifierT, y adicionalmente en runtime se le asocia con la interfaz deClassifierPoolObsrver al
    definir estos metodos internamente
    """

    def observe_wait(self, *, duration_seconds: float, outcome: str) -> None: ...

    def set_state(self, *, capacity: int, available: int, borrowed: int) -> None: ...

    def record_timeout(self) -> None: ...


class ClassifierPool(Generic[ClassifierT]):
    """
    Clase que implementa realmente una ClassifierPool generica que puede contener cualquier tipo de clasificador que sea
     parte de la interface TrafficClassifier. La clase implementa un mecanismo de adquisicion de recursos basado en
     un Queue de asyncio que permite manejar de forma segura la concurrencia de los accesos al pool de manera
     concurrente y segura.

    La idea es que la queue de trabajo de los modelos permiten un numero entre 1 y 32 elementso, que para la API se
    configuran como 5 elementos. Al ser una pool con mecanismos asincronos. Cuando no haya un modelo libre para su
    seleccion, la corutina que se encarga de recibir la peticion HTTP y responder hace un wait hasta que haya un
    modelo libre
    """

    def __init__(self, classifiers: list[ClassifierT], observer: ClassifierPoolObserver | None = None) -> None:
        """
        Inicializa el pool con una lista no vacia de clasificadores.

        Args:
            classifiers: lista de instancias de clasificadores.
            observer: observador opcional para registrar metricas de estado de Prometheus.

        """

        if not classifiers:
            raise ValueError("El pool requiere al menos una instancia de clasificador.")

        #? Configuramos la capacidad, el observer y generamos la queue de asyncio
        self._capacity = len(classifiers)
        self._observer = observer
        #? Creamos la Queue de asyncio con la capacidad maxima la cantidad de clasificadores registrados
        self._available: asyncio.Queue[ClassifierT] = asyncio.Queue(maxsize=self._capacity)

        #? Registramos todos los clasificadores dentro de la queue. EN este caso usamos el metodo
        #? put_nowait dado que el agregar es sincrono y la queue esta vacia por lo que no violamos la regla de
        #? capacidad
        for classifier in classifiers:
            self._available.put_nowait(classifier)

        #? Usamos los metodos de la interfaz de registro de datos de metricas para registrar los detalles
        #? del estado de la API
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
        """
        Funcion que permite adquirir un clasificador del pool de forma segura y concurrente. Esta funcion es utilizada por la API
        para obtener un clasificador disponible antes de ejecutar una inferencia. Si no hay ningun clasificador
        disponible, la funcion espera hasta que haya uno disponible o hasta que se alcance el timeout establecido
        en las settings de la API

        EL flujo de este mecanismo parte desde la seccion de sdn_mpls_ml_inference_route.py en donde el bloque de codigo de


        ```
        async with services.classifier_pool.acquire(
            timeout_seconds=settings.request_timeout_seconds
            ) as classifier:
                queue_wait_ms = round((time.perf_counter() - queue_wait_started) * 1000, 3)
                try:
                    prediction = await execute_instrumented_inference(
                        classifier=classifier,
                        packet_features=packet_features,
                        classification_mode=classification_mode,
                        limiter=services.inference_thread_limiter,
                        enabled=metrics_enabled,
                        run_sync=to_thread.run_sync,
                    )
        ```

        En este caso al entrar al funcionamiento, el bloque intenta realizar una peticion a la pool de clasificadores
        con un tiempo de timeout en un bloque async. Dado que el contexto de esta request usa @asynccontextmanager,
        el flujo de trabajo cambia. Al ejecutar esta sentencia ejecuta inicialmente este metodo, e intenta obtener
        una instancia desde el sistema.
        - Si hay una instancia el metodo de _availble.get() funciona directamente y retorna el objeto.
        - Si no hay una instancia, el bloque de asyncio.wait_for() le permite esperar por los segundos de espera
        configurados. Ademas, si no hub una instancia, el event loop se reinicia y la corutina que manejaba la
        solicitud HTTP se maneja en estado wait hasta que se la despierte con un nuevo elemento libre.

        Luego de obtener la instancia llegamos a un yield que retorna el control al programa principal, en este caso
        la API, y se ejecuta el codigo de inferencia.

        Una vez terminada la inferencia, el recurso se libera automaticamente y se ejecuta el bloque finally
        que devuelve el recurso a la pool. Es importante notar que el contexto de esta funcion es un
        asynccontextmanager, por lo tanto el flujo de ejecucion cambia y se ejecuta el yield, se retorna el
        control al programa principal y luego se ejecuta el finally cuando se termina el bloque de uso del
        clasificador adquirido.

        :param self: self instance
        :param timeout_seconds: timeout en segundos para esperar un clasificador disponible
        :return: AsyncIterator[ClassifierT] que representa el clasificador adquirido
        """

        wait_started = time.perf_counter()
        try:
            #? usamos asyncio.wait_for para esperar a que haya un elemento disponible en la queue o lanzar un timeout
            classifier = await asyncio.wait_for(
                self._available.get(),  #* Metodo a ejecutar y que puede causar que esperemos por el tiempo configurado
                timeout=timeout_seconds)
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

        #? Si obtenemos la instancia sin errores registramos los estados en prometheus
        self._observe_wait(time.perf_counter() - wait_started, "acquired")
        self._publish_state()

        #? Aqui entramos a un bloque de retorno al flujo de la API, @asynccontextmanager cambia el flujo de un yield en
        #? donde el objeto retorna control al programa de la API para continuar con la clasificacion
        #? Al final de su uso el recuros se libera y el control retorna aqui y se ejecuta el bloque finally
        try:
            yield classifier
        finally:
            #? Regresamos el clasificador a la pool inclusive si hubo errores, se cancelo la corutina o si no se
            #? maneja errores correctamente en el lado de la API
            self._available.put_nowait(classifier)
            self._publish_state()

    def _observe_wait(self, duration_seconds: float, outcome: str) -> None:
        """
        Funcion que permite registrar en el observer la duracion del tiempo de espera de un recurso y el resultado de
        ese tiempo
        :param duration_seconds: duración en segundos del tiempo de espera
        :param outcome: resultado del tiempo de espera
        :return: None
        """
        if self._observer is not None:
            self._observer.observe_wait(duration_seconds=duration_seconds, outcome=outcome)

    def _publish_state(self) -> None:
        """
        Funcion que permite registrar en el observer el estado de la API correspondiente a la capacidad de la pool
        generada, los modelos disponibles y los modelos tomados en prestamo por alguna inferencia
        :return: None
        """
        if self._observer is not None:
            self._observer.set_state(
                capacity=self.capacity,
                available=self.available,
                borrowed=self.borrowed,
            )
