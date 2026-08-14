/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.serialization.xml;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sma.sdn.model.BgpLsTopologyNode;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Define la clase {@code BgpLsTopologyXmlDeserializerTest} dentro del controlador SDN-MPLS-ML.
 * Esta unidad encapsula una responsabilidad
 * responsabilidad
 * concreta del flujo de control, de los modelos de dominio o de las defensas aplicadas sobre las llamadas ODL.
 */
class BgpLsTopologyXmlDeserializerTest {
    /**
     * Ejecuta la operacion {@code parsesRouterIdAndGraphNodeId} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @throws RuntimeException si la validacion, el parseo, la comunicacion o la consistencia requerida fallan
     */
    @Test
    void parsesRouterIdAndGraphNodeId() {
        final String xml = """
                <topology>
                    <topology-id>sma-bgp-linkstate-topology</topology-id>
                    <node>
                        <node-id>
                            bgpls://Ospf:36/type=node&amp;as=65000&amp;domain=0&amp;area=0&amp;router=185273099
                        </node-id>
                        <igp-node-attributes>
                            <router-id>11.11.11.11</router-id>
                            <ospf-node-attributes>
                                <ted>
                                    <te-router-id-ipv4>11.11.11.11</te-router-id-ipv4>
                                </ted>
                            </ospf-node-attributes>
                        </igp-node-attributes>
                    </node>
                </topology>
                """;

        final List<BgpLsTopologyNode> nodes = new BgpLsTopologyXmlDeserializer().deserialize(xml);

        assertEquals(1, nodes.size());
        assertEquals("11.11.11.11", nodes.get(0).routerId());
        assertEquals(185273099L, nodes.get(0).graphNodeId());
    }
}
