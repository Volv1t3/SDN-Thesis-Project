"""
SDN-MPLS-ML Tech Demonstrator
Santiago Arellano 00328370

Archivo que centraliza los mensajes de la aplicacion entera, desde respuestas automatizadas para las API, endpoints HTTP,
errores y logs

Notas:
- Los nombres de campos JSON, eventos, codigos y componentes permanecen en ingles para su filtrado en Grafana.
- Los textos de este modulo usan espanol ASCII para preservar el estandar del proyecto y mantener los mensajes
claros en consolas u otros sistemas.
"""

from __future__ import annotations


class Messages:
    """
    Agrupa mensajes humanos reutilizables de la API.


    Estos mensajes definen varios de los posibles serrores que se pueden dar dentro de la aplicacion, tanto en las areas de
    validacion de contenido, asi como en areas de validacion de mensajes, cuerpos en requests de la api y posibles fallas
    de la api propia. Todas estas strings se exportan y usan de manera general en las respuestas de la API y Logs para
    que sean de facil entendimiento a la hora de debuggear el codigo

    """

    #! Mensajes de errores HTTP y respuestas a mensajes incorrectos a la API
    INTERNAL_ERROR = "Ocurrio un error interno inesperado."
    INVALID_JSON = "El cuerpo de la solicitud no contiene JSON valido."
    INVALID_CONTENT_LENGTH = "El encabezado Content-Length no es valido."
    REQUEST_TOO_LARGE = "El cuerpo de la solicitud supera el tamano maximo configurado."
    REQUEST_VALIDATION_FAILED = "El cuerpo de la solicitud no cumple con el contrato requerido."
    MODEL_ETHERTYPE_UNSUPPORTED = "El modelo cargado solo admite trafico IPv4 con EtherType 2048."
    MODEL_NOT_READY = "El servicio de inferencia no esta listo."
    MODEL_INFERENCE_FAILED = "No fue posible determinar la categoria del trafico."
    MODEL_OUTPUT_INVALID = "La salida producida por el modelo no cumple con el contrato requerido."
    POLICY_MAPPING_FAILED = "No fue posible resolver la politica de trafico."
    INFERENCE_CAPACITY_EXCEEDED = "No se obtuvo una instancia de clasificador dentro del tiempo permitido."
    HTTP_NOT_FOUND = "No se encontro la ruta solicitada."

    #! Mensajes similares a los anteriores, pero usados en los logs del sistema
    INVALID_JSON_RECEIVED = "Se recibio un cuerpo JSON invalido."
    REQUEST_VALIDATION_LOG = "La solicitud no supero la validacion del esquema."
    INVALID_CONTENT_LENGTH_LOG = "La solicitud fue rechazada porque el encabezado Content-Length es invalido."
    REQUEST_TOO_LARGE_LOG = "La solicitud fue rechazada porque excede el tamano permitido."
    CLASSIFICATION_REJECTED = "La solicitud de clasificacion fue rechazada."
    MODEL_NOT_READY_LOG = "La solicitud fue rechazada porque el modelo no esta listo."
    MODEL_INFERENCE_FAILED_LOG = "La inferencia del modelo fallo."
    MODEL_OUTPUT_INVALID_LOG = "La salida del modelo no supero la validacion del contrato."
    POLICY_MAPPING_FAILED_LOG = "La resolucion de la politica de trafico fallo."
    INFERENCE_CAPACITY_EXCEEDED_LOG = "La solicitud excedio la capacidad disponible de inferencia."
    POLICY_FALLBACK_APPLIED = "Se aplico la politica predeterminada."
    CLASSIFICATION_COMPLETED = "La clasificacion de trafico fue completada."
    HTTP_REQUEST_NOT_FOUND = "No se encontro la ruta HTTP solicitada."
    HTTP_REQUEST_FAILED = "La solicitud HTTP fallo."
    UNHANDLED_EXCEPTION = "Ocurrio una excepcion no controlada."

    #! Mensajes usados para definir los pasos en el startup check de la aplicacion, que revisa si la metadata
    #! cargada del sistem vale, si el modelo se ha cargado, es valido y tambien si sirve, y al final si los mapeos
    #! de politicas son validos
    STARTUP_VALIDATION_STARTED = "Se inicio la validacion de arranque del servicio."
    CONFIGURATION_PREFLIGHT_PASSED = "La validacion preliminar de configuracion fue exitosa."
    ARTIFACT_CONFIGURATION_RESOLVED = "La configuracion de artefactos fue resuelta."
    ARTIFACT_VALIDATION_PASSED = "La validacion de artefactos fue exitosa."
    METADATA_VALIDATION_PASSED = "La validacion de metadatos fue exitosa."
    MODEL_LOAD_STARTED = "Se inicio la carga del modelo."
    MODEL_LOAD_PASSED = "El modelo fue cargado correctamente."
    SYNTHETIC_INFERENCE_PASSED = "La inferencia sintetica de arranque fue exitosa."
    POLICY_MAP_VALIDATION_PASSED = "La validacion del mapa de politicas fue exitosa."
    CLASSIFIER_POOL_INITIALIZATION_STARTED = "Se inicio la inicializacion del pool de clasificadores."
    CLASSIFIER_POOL_INSTANCE_READY = "Una instancia del pool de clasificadores esta lista."
    CLASSIFIER_POOL_READY = "El pool de clasificadores esta listo."
    CLASSIFIER_POOL_INITIALIZATION_FAILED = "La inicializacion del pool de clasificadores fallo."
    SERVICE_READY = "El servicio de inferencia esta listo."
    SERVICE_NOT_READY = "El servicio de inferencia no esta disponible."

    #! Mensajes de advertencia y errores en terminos de configuracion, lo que nos permite identificar en los Logs de la ap
    #! si hay errores en las variables de entorno en las que depende la aplicacion
    APP_NAME_REQUIRED = "APP_NAME debe configurarse con un valor no vacio."
    APP_VERSION_REQUIRED = "APP_VERSION debe configurarse con un valor no vacio."
    CLASSIFICATION_MODE_UNSUPPORTED = "CLASSIFICATION_MODE no es un valor soportado."
    PORT_INVALID = "PORT debe ser un entero valido."
    PORT_RANGE = "PORT debe estar entre 1 y 65535."
    LOG_LEVEL_UNSUPPORTED = "LOG_LEVEL debe ser uno de los valores soportados."
    CLASSIFIER_POOL_SIZE_RANGE = "CLASSIFIER_POOL_SIZE debe estar entre 1 y 32."
    CONFIG_DIR_REQUIRED = "CONFIG_DIR debe configurarse con un valor no vacio."
    POLICY_FILENAME_REQUIRED = "POLICY_FILENAME debe configurarse con un valor no vacio."
    MAX_REQUEST_BODY_BYTES_POSITIVE = "MAX_REQUEST_BODY_BYTES debe ser un entero positivo."
    PROBABILITY_TOLERANCE_POSITIVE = "PROBABILITY_TOLERANCE debe ser un numero positivo."
    MIN_POLICY_CONFIDENCE_RANGE = "MIN_POLICY_CONFIDENCE debe estar entre 0.0 y 1.0."
    REQUEST_TIMEOUT_SECONDS_POSITIVE = "REQUEST_TIMEOUT_SECONDS debe ser un entero positivo."
    MIN_TUNNEL_BANDWIDTH_RANGE = "MIN_TUNNEL_BANDWIDTH_KBPS debe estar entre 1 y 100000."
    MAX_TUNNEL_BANDWIDTH_RANGE = "MAX_TUNNEL_BANDWIDTH_KBPS debe estar entre 1 y 100000."
    TUNNEL_BANDWIDTH_BOUNDS = "MIN_TUNNEL_BANDWIDTH_KBPS no debe superar MAX_TUNNEL_BANDWIDTH_KBPS."
    ENABLE_POLICY_MAPPING_BOOLEAN = "ENABLE_POLICY_MAPPING debe ser un valor booleano."
    ENABLE_PROMETHEUS_METRICS_BOOLEAN = "ENABLE_PROMETHEUS_METRICS debe ser un valor booleano."
    METRICS_PATH_INVALID = "METRICS_PATH debe ser una ruta HTTP absoluta valida."
    PROMETHEUS_METRICS_ENABLED = "La exportacion de metricas Prometheus esta habilitada."
    PROMETHEUS_METRICS_DISABLED = "La exportacion de metricas Prometheus esta deshabilitada."
    PROMETHEUS_MULTIPROCESS_CONFIGURATION_INVALID = (
        "La configuracion multiproceso de Prometheus no es valida."
    )
    MODEL_DIR_REQUIRED_MODEL_MODE = "MODEL_DIR debe configurarse en modo model."
    MODEL_FILENAME_REQUIRED_MODEL_MODE = "MODEL_FILENAME debe configurarse en modo model."
    MODEL_METADATA_FILENAME_REQUIRED_MODEL_MODE = "MODEL_METADATA_FILENAME debe configurarse en modo model."
    DETERMINISTIC_RULE_FILENAME_REQUIRED = (
        "DETERMINISTIC_RULE_FILENAME debe configurarse en modo deterministic_test."
    )

    #! Mensajes de error de carga de los archivos del modelo, metadata o configuracion, permitiendo reportar errores no solo
    #! de ausencia de los archivos, sino de configuracion y sintaxis, es decir, si se rompe un modelo de datos, etc.
    MODEL_METADATA_INVALID_JSON = "El archivo de metadatos del modelo no contiene JSON valido."
    MODEL_METADATA_INCOMPATIBLE = "Los metadatos del modelo no cumplen el contrato de compatibilidad."
    MODEL_METADATA_SCHEMA_INVALID = "El archivo de metadatos del modelo no supero la validacion del esquema."
    POLICY_SCHEMA_INVALID = "El archivo de politicas no supero la validacion del esquema."
    POLICY_VERSION_UNSUPPORTED = "El archivo de politicas usa una version de esquema no soportada."
    DETERMINISTIC_RULE_SCHEMA_INVALID = "El archivo de reglas deterministicas no supero la validacion del esquema."
    SYNTHETIC_ETHERTYPE_INVALID = "La auto-prueba sintetica del modelo debe usar un EtherType soportado."
    XGBOOST_RUNTIME_LOAD_FAILED = "No fue posible cargar el runtime de XGBoost."
    MODEL_ARTIFACT_LOAD_FAILED = "El runtime de XGBoost no pudo cargar el artefacto del modelo."
    MODEL_FEATURE_COUNT_INSPECTION_FAILED = "No fue posible inspeccionar la cantidad de features del modelo."
    MODEL_FEATURE_COUNT_MISMATCH = (
        "La cantidad de features del modelo no coincide con el contrato requerido de cuatro features."
    )
    MODEL_OBJECTIVE_INCOMPATIBLE = (
        "El objetivo del modelo cargado no es compatible con inferencia multiclase probabilistica."
    )
    MODEL_CLASS_COUNT_MISMATCH = (
        "La cantidad de clases del modelo no coincide con el contrato requerido de siete clases."
    )
    SYNTHETIC_PROBABILITY_COUNT_INVALID = "La inferencia sintetica no devolvio exactamente siete probabilidades."
    SYNTHETIC_PROBABILITY_NONFINITE = "La inferencia sintetica produjo probabilidades no finitas."
    SYNTHETIC_PROBABILITY_BOUNDS = "La inferencia sintetica produjo probabilidades fuera del rango [0.0, 1.0]."
    SYNTHETIC_CLASS_ID_INVALID = "La inferencia sintetica produjo un identificador de clase invalido."
    SYNTHETIC_CLASS_NAME_INVALID = "La inferencia sintetica produjo un nombre de clase invalido."
    POLICY_UNKNOWN_CLASS = "El archivo de politicas contiene una clase de trafico desconocida."
    POLICY_MISSING_CLASS = "El archivo de politicas no contiene todas las clases de trafico requeridas."
    DEFAULT_PROFILE_BANDWIDTH_ZERO = (
        "El perfil best_effort predeterminado debe conservar requested_bandwidth_kbps en 0."
    )
    POLICY_SERIALIZATION_FAILED = "No fue posible serializar el perfil de politica despues de la validacion."

    #! Mensajes de error que permiten retornar informacion sobre errores del modelo, por ejemplo si un modelo ha pasado la configuracion
    #! y validacion, pero empieza a retornar resultados erroneos, tenemos mensajes exactos para retornar a la API y los logs
    MODEL_OUTPUT_MULTIPLE_ROWS = "El modelo devolvio mas de una fila de prediccion."
    MODEL_OUTPUT_SHAPE_UNSUPPORTED = "El modelo devolvio una forma de salida no soportada."
    MODEL_OUTPUT_PROBABILITY_COUNT = "La salida del modelo no contiene exactamente siete probabilidades."
    MODEL_OUTPUT_NONFINITE = "La salida del modelo contiene probabilidades no finitas."
    MODEL_OUTPUT_BOUNDS = "La salida del modelo contiene probabilidades fuera del rango [0.0, 1.0]."
    MODEL_OUTPUT_SUM = "Las probabilidades del modelo no suman 1 dentro de la tolerancia."
    POLICY_FILE_INVALID_JSON = "El archivo de politicas no contiene JSON valido."
    POLICY_FILE_READ_FAILED = "No fue posible leer el archivo de politicas."
    DETERMINISTIC_RULE_FILE_INVALID_JSON = "El archivo de reglas deterministicas no contiene JSON valido."
    DETERMINISTIC_RULE_FILE_READ_FAILED = "No fue posible leer el archivo de reglas deterministicas."
    PORT_MAP_KEYS_DECIMAL = "Las claves del mapa de puertos deben ser numeros decimales."
    PORT_MAP_KEYS_RANGE = "Las claves del mapa de puertos deben estar entre 0 y 65535."
    DETERMINISTIC_RULE_SCHEMA_VERSION = "La version del esquema de reglas deterministicas debe ser 1.0."
    DETERMINISTIC_STREAMING_FALLBACK = "El fallback deterministico debe permanecer como STREAMING."
    DETERMINISTIC_UNKNOWN_DESTINATION_CLASS = (
        "Las reglas de puerto de destino referencian una clase de trafico desconocida."
    )
    DETERMINISTIC_UNKNOWN_SOURCE_CLASS = (
        "Las reglas de puerto de origen referencian una clase de trafico desconocida."
    )
    DETERMINISTIC_STREAMING_DIRECT_RULE = (
        "STREAMING debe mantenerse como fallback general y no como regla directa de puerto."
    )
    METADATA_SCHEMA_VERSION = "La version del esquema de metadatos debe ser 1.0."
    METADATA_MODEL_FORMAT_INVALID = "model_format debe ser xgboost_booster_json."
    METADATA_FEATURE_ORDER_INVALID = "feature_order no coincide con el contrato requerido del clasificador."
    METADATA_FEATURE_TYPES_INVALID = "feature_types debe definir exactamente las features soportadas."
    METADATA_FEATURE_TYPES_INTEGER = "Todas las features deben declararse con tipo integer."
    METADATA_CLASS_TO_ID_COUNT = "class_to_id debe contener exactamente siete entradas."
    METADATA_ID_TO_CLASS_COUNT = "id_to_class debe contener exactamente siete entradas."
    METADATA_CLASS_IDS_CONTIGUOUS = "Los valores de class_to_id deben ser unicos y contiguos de 0 a 6."
    METADATA_CLASS_TO_ID_CONTRACT = "class_to_id no coincide con el contrato requerido del clasificador."
    METADATA_ID_TO_CLASS_CONTRACT = "id_to_class no coincide con el contrato requerido del clasificador."
    METADATA_FILE_INVALID_JSON = "El archivo de metadatos no contiene JSON valido."
    PROFILE_NAME_REQUIRED = "profile_name debe configurarse con un valor no vacio."
    POLICY_SCHEMA_VERSION = "La version del esquema de politicas debe ser 1.0."
    NON_TCP_UDP_ZERO_PORTS = (
        "src_port y dst_port deben permanecer en 0 cuando el protocolo no es TCP ni UDP."
    )
    REQUEST_ID_NOT_INITIALIZED = "El identificador de correlacion del request no fue inicializado."
    MODEL_FILE_MISSING = "No se encontro el archivo del modelo: {path}"
    METADATA_FILE_MISSING = "No se encontro el archivo de metadatos: {path}"
    MODEL_BUNDLE_SYNTHETIC_PROBABILITY_COUNT = (
        "La auto-prueba sintetica no devolvio probabilidades para todas las clases."
    )
    MODEL_BUNDLE_SYNTHETIC_NONFINITE = "La auto-prueba sintetica produjo probabilidades no finitas."


    #? Toda esta seccion define metodos staticos para la clase que nos permiten decorar frases genericas
    @staticmethod
    def artifact_path_resolution(configured_filename: str) -> str:
        """
        Permite adornar una frase referencial para indicar la 'falta de un archivo dado una ruta no resuelta' para un
        tipo de artefacto bajo un nombre.
        :param configured_filename: nombre del archivo
        :return: string concatenada
        """
        return f"No se pudo resolver la ruta del artefacto configurado {configured_filename}."

    @staticmethod
    def artifact_not_found(configured_filename: str) -> str:
        """
        Permite adornar una frase referencial para indicar la 'falta de un archivo dado una ruta resuelta' para un
        tipo de artefacto bajo un nombre.
        :param configured_filename: nombre del archivo
        :return: string concatenada
        """
        return f"No se encontro el artefacto configurado {configured_filename}."

    @staticmethod
    def artifact_not_regular_file(configured_filename: str) -> str:
        """
        Permite documentar una frase referencial para indicar que un artefacto cargado y leido no es un archivo regular.
        :param configured_filename: nombre del archivo
        :return: string concatenada
        """
        return f"El artefacto configurado {configured_filename} no es un archivo regular."

    @staticmethod
    def artifact_not_readable(configured_filename: str) -> str:
        """
        Permite documentar una frase referencial para indicar un archivo que el proceso no tiene acceso de lectura
        :param configured_filename: nombre del archivo
        :return: string concatenada
        """
        return f"El artefacto configurado {configured_filename} no se puede leer."

    @staticmethod
    def artifact_empty(configured_filename: str) -> str:
        """
        Permite documentar una frase referencial para indicar un archivo que fue abierto pero esta vacio
        :param configured_filename: nombre del archivo
        :return: string concatenada
        """
        return f"El artefacto configurado {configured_filename} esta vacio."

    @staticmethod
    def artifact_not_utf8(configured_filename: str) -> str:
        """
        Permite documentar una frase referencial para indicar que un archivo de configuracion o modelo no tiene un formato
        utf8 para su lectura.
        :param configured_filename: nombre del archivo
        :return: string concatenada
        """
        return f"El artefacto configurado {configured_filename} no pudo abrirse como UTF-8."

    @staticmethod
    def policy_file_validation_failed(details: str) -> str:
        """
        Permite documentar una frase referencial para indicar que un archivo cargado fallo el proceso de validacion sea de
        Pydantic o la validacion propia de la aplicacion
        :param details: detalles del problema de validacion
        :return: string concatenada
        """
        return f"La validacion del archivo de politicas fallo: {details}"

    @staticmethod
    def deterministic_rule_validation_failed(details: str) -> str:
        """
        Permite documentar una frase de referencia de que el archivo de reglas deterministicas, usado en el caso de que el
        sistema se ejecute en modo DETERMINISTIC_TEST no pase validacion
        :param details: detalles del problema de validacion
        :return: string concatenada
        """
        return f"La validacion del archivo de reglas deterministicas fallo: {details}"

    @staticmethod
    def unexpected_model_name(model_name: str) -> str:
        """
        Permite documentar una frase de referencia de que el archivo de reglas del modelo no tiene el nombre de modelo esperado
        y esto ha resultado en un error de validacion.
        :param model_name: nombre del modelo esperado
        :return: string concatenada
        """
        return f"model_name no coincide con el valor esperado: {model_name}"

    @staticmethod
    def metadata_file_read_failed(path: str) -> str:
        """
        Permite documentar una frase de referencia que determina que un archivo de metadatos del modelo no fue leido correctamente
        :param path: ruta del archivo de configuracion
        :return: string concatenada
        """
        return f"No fue posible leer el archivo de metadatos: {path}"

    @staticmethod
    def metadata_validation_failed(details: str) -> str:
        """
        Permite docuemntar una frase de referencia que determina que un archivo de metadatos fue cargado pero no
        paso una validacion sea de Pydantic o configurada en el codigo
        :param details: detalles del error de validacion
        :return: string concatenada
        """
        return f"La validacion de metadatos fallo: {details}"

    @staticmethod
    def policy_bandwidth_below_minimum(class_name: str) -> str:
        """
        Permite documentar una frase de referencia que determina que un archivo de politicas fue cargado pero el ancho de banda
        de la politica se encuentra por debajo del minimo permitido por la aplicacion
        :param class_name: detalle de la clase del archivo de politica que no paso la prueba
        :return: string concatenada
        """
        return f"El ancho de banda de la politica {class_name} esta por debajo del minimo del demostrador."

    @staticmethod
    def policy_bandwidth_above_maximum(class_name: str) -> str:
        """
        Permite documentar una frase de referencia que determina que un archivo de politicas fue cargado pero el ancho de banda
        de la politica se encuentra por encima del maximo permitido por la aplicacion
        :param class_name: detalle de la clase del archivo de politica que no paso la prueba
        :return: string concatenada
        """
        return f"El ancho de banda de la politica {class_name} supera el maximo del demostrador."
