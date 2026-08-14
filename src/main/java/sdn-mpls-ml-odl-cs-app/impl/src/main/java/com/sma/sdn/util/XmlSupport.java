/*
 * Copyright (c) 2026 Santiago Arellano and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package com.sma.sdn.util;

import com.sma.sdn.observability.StructuredLogger;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Define la clase {@code XmlSupport} dentro del controlador SDN-MPLS-ML.
 * Esta unidad encapsula una responsabilidad
 * responsabilidad
 * concreta del flujo de control, de los modelos de dominio o de las defensas aplicadas sobre las llamadas ODL.
 */
public final class XmlSupport {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(XmlSupport.class);
    /**
     * Ejecuta la operacion {@code XmlSupport} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     */
    private XmlSupport() {
    }

    /**
     * Ejecuta la operacion {@code parse} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param xml valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     *
     * @throws RuntimeException si la validacion, el parseo, la comunicacion o la consistencia requerida fallan
     */
    public static Document parse(final String xml) {
        try {
            final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            final Document document = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            LOG.trace("xml_document_parsed", "parse",
                    "Se analizo un documento XML mediante la configuracion segura",
                    StructuredLogger.fields("serialized_characters", xml.length(),
                            "root_element", document.getDocumentElement().getLocalName()));
            return document;
        } catch (Exception e) {
            throw new IllegalArgumentException("No fue posible analizar la respuesta XML", e);
        }
    }

    /**
     * Ejecuta la operacion {@code nodes} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param node valor requerido para ejecutar esta operacion
     *
     * @param expression valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     */
    public static NodeList nodes(final Node node, final String expression) {
        try {
            final XPath xpath = XPathFactory.newInstance().newXPath();
            final NodeList result = (NodeList) xpath.evaluate(expression, node, XPathConstants.NODESET);
            LOG.trace("xpath_nodes_evaluated", "nodes",
                    "Se evaluo una expresion XPath que devuelve nodos",
                    StructuredLogger.fields("expression", expression, "result_count", result.getLength()));
            return result;
        } catch (Exception e) {
            throw new IllegalArgumentException("No fue posible evaluar la expresion XPath: " + expression, e);
        }
    }

    /**
     * Ejecuta la operacion {@code string} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param node valor requerido para ejecutar esta operacion
     *
     * @param expression valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     */
    public static String string(final Node node, final String expression) {
        try {
            final XPath xpath = XPathFactory.newInstance().newXPath();
            final String value = xpath.evaluate(expression, node);
            return value == null || value.isBlank() ? null : value.trim();
        } catch (Exception e) {
            throw new IllegalArgumentException("No fue posible evaluar la expresion XPath: " + expression, e);
        }
    }

    /**
     * Ejecuta la operacion {@code integer} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param node valor requerido para ejecutar esta operacion
     *
     * @param expression valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     */
    public static Integer integer(final Node node, final String expression) {
        final String value = string(node, expression);
        return value == null ? null : Integer.valueOf(value);
    }

    /**
     * Ejecuta la operacion {@code longValue} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param node valor requerido para ejecutar esta operacion
     *
     * @param expression valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     */
    public static Long longValue(final Node node, final String expression) {
        final String value = string(node, expression);
        return value == null ? null : Long.valueOf(value);
    }

    /**
     * Ejecuta la operacion {@code bool} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param node valor requerido para ejecutar esta operacion
     *
     * @param expression valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     */
    public static Boolean bool(final Node node, final String expression) {
        final String value = string(node, expression);
        return value == null ? null : Boolean.valueOf(value);
    }

    /**
     * Ejecuta la operacion {@code elementNamed} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param node valor requerido para ejecutar esta operacion
     *
     * @param localName valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     */
    public static boolean elementNamed(final Node node, final String localName) {
        return node instanceof Element && localName.equals(node.getLocalName());
    }

    /**
     * Ejecuta la operacion {@code escape} dentro del componente correspondiente.
     *
     * <p>Pasos:
     * <ol>
     *   <li>Valida o consume los parametros de entrada necesarios.</li>
     *   <li>Ejecuta la operacion local, HTTP, XML, JSON o de registro que corresponde.</li>
     *   <li>Devuelve el resultado tipado o actualiza el estado interno de forma controlada.</li>
     * </ol>
     *
     * @param value valor requerido para ejecutar esta operacion
     *
     * @return resultado calculado, estado encontrado o modelo construido por la operacion
     */
    public static String escape(final String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
