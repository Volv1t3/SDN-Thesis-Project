/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.metrics;

import com.sma.sdn.observability.StructuredLogger;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Define la clase {@code SdnMplsMlMetrics} dentro del controlador SDN-MPLS-ML.
 * Esta unidad encapsula una responsabilidad
 * responsabilidad
 * concreta del flujo de control, de los modelos de dominio o de las defensas aplicadas sobre las llamadas ODL.
 */
public final class SdnMplsMlMetrics {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(SdnMplsMlMetrics.class);
    private final Map<String, LongAdder> counters = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> gauges = new ConcurrentHashMap<>();

    /**
     * Ejecuta la operacion {@code increment} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param counterName valor requerido para ejecutar esta operacion
     */
    public void increment(final String counterName) {
        final LongAdder counter = counters.computeIfAbsent(counterName, key -> new LongAdder());
        counter.increment();
        LOG.trace("metric_counter_incremented", "increment",
                "Se incremento un contador operativo",
                StructuredLogger.fields("counter_name", counterName, "value", counter.sum()));
    }

    /**
     * Ejecuta la operacion {@code value} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param counterName valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     */
    public long value(final String counterName) {
        final AtomicLong gauge = gauges.get(counterName);
        if (gauge != null) {
            return gauge.get();
        }
        final LongAdder value = counters.get(counterName);
        return value == null ? 0L : value.sum();
    }

    /**
     * Establece una metrica de estado o marca temporal cuyo valor no debe acumularse.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Crea la metrica atomica cuando todavia no existe.</li>
     *   <li>Sustituye el valor anterior de forma visible para otros hilos.</li>
     *   <li>Registra el nuevo estado a nivel de traza.</li>
     * </ol>
     *
     * @param metricName nombre estable de la metrica
     * @param value nuevo valor absoluto
     */
    public void set(final String metricName, final long value) {
        gauges.computeIfAbsent(metricName, key -> new AtomicLong()).set(value);
        LOG.trace(
                "metric_gauge_updated",
                "set",
                "Se actualizo una metrica de estado.",
                StructuredLogger.fields("metric_name", metricName, "value", value));
    }

    /**
     * Ejecuta la operacion {@code snapshot} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     */
    public Map<String, Long> snapshot() {
        final Map<String, Long> values = new ConcurrentHashMap<>();
        counters.forEach((key, value) -> values.put(key, value.sum()));
        gauges.forEach((key, value) -> values.put(key, value.get()));
        final Map<String, Long> snapshot = Map.copyOf(values);
        LOG.debug("metrics_snapshot_created", "snapshot",
                "Se genero una instantanea de las metricas operativas",
                StructuredLogger.fields("counter_count", snapshot.size(), "counters", snapshot));
        return snapshot;
    }
}
