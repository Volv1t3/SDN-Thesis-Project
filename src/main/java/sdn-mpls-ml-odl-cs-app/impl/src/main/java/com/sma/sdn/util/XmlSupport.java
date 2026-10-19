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
 * <p>Provides namespace-aware DOM parsing, XPath extraction, scalar conversion,
 * and XML text escaping for controller serializers and deserializers.</p>
 * <p>Parsing enables secure processing and rejects document type declarations;
 * XPath evaluation uses a newly created evaluator without a namespace context.</p>
 */
public final class XmlSupport {
    /** <p>Structured logger for parsed documents and node-set XPath results.</p> */
    private static final StructuredLogger LOG = StructuredLogger.getLogger(XmlSupport.class);
    /**
     * <p>Prevents construction of this static utility class.</p>
     * <p>Construction proceeds as follows:</p>
     * <ol><li>Complete the private constructor without initializing instance state.</li></ol>
     */
    private XmlSupport() {
    }

    /**
     * <p>Parses an XML string into a namespace-aware DOM document.</p>
     * <p>Parsing proceeds as follows:</p>
     * <ol><li>Configure a new factory for namespace awareness, secure processing,
     * and rejection of document type declarations.</li><li>Parse the UTF-8 bytes,
     * log the root, and wrap parsing or configuration exceptions.</li></ol>
     * @param xml serialized XML document
     * @return parsed DOM document
     * @throws IllegalArgumentException if parsing or parser configuration fails
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
     * <p>Evaluates an XPath expression as a node set against a DOM node.</p>
     * <p>Evaluation proceeds as follows:</p>
     * <ol><li>Create an XPath evaluator, evaluate the expression as a node set,
     * and log its result count; wrap evaluation exceptions.</li></ol>
     * @param node XPath context node
     * @param expression XPath expression
     * @return matching DOM nodes
     * @throws IllegalArgumentException if XPath evaluation fails
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
     * <p>Evaluates an XPath expression as a trimmed string, treating blank values as absent.</p>
     * <p>Evaluation proceeds as follows:</p>
     * <ol><li>Evaluate the expression using a new XPath evaluator.</li>
     * <li>Return null for a null or blank result, otherwise return the trimmed value.</li></ol>
     * @param node XPath context node
     * @param expression XPath expression
     * @return trimmed value or null when blank
     * @throws IllegalArgumentException if XPath evaluation fails
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
     * <p>Extracts an optional integer from an XPath string result.</p>
     * <p>Conversion proceeds as follows:</p>
     * <ol><li>Read the trimmed string; return null if absent or parse it as an integer.</li></ol>
     * @param node XPath context node
     * @param expression XPath expression
     * @return parsed integer or null when blank
     * @throws IllegalArgumentException if XPath evaluation or integer parsing fails
     */
    public static Integer integer(final Node node, final String expression) {
        final String value = string(node, expression);
        return value == null ? null : Integer.valueOf(value);
    }

    /**
     * <p>Extracts an optional long integer from an XPath string result.</p>
     * <p>Conversion proceeds as follows:</p>
     * <ol><li>Read the trimmed string; return null if absent or parse it as a long.</li></ol>
     * @param node XPath context node
     * @param expression XPath expression
     * @return parsed long or null when blank
     * @throws IllegalArgumentException if XPath evaluation or long parsing fails
     */
    public static Long longValue(final Node node, final String expression) {
        final String value = string(node, expression);
        return value == null ? null : Long.valueOf(value);
    }

    /**
     * <p>Extracts an optional Boolean from an XPath string result.</p>
     * <p>Conversion proceeds as follows:</p>
     * <ol><li>Read the trimmed string and return null if absent; otherwise apply
     * {@link Boolean#valueOf(String)}, which treats only case-insensitive true as true.</li></ol>
     * @param node XPath context node
     * @param expression XPath expression
     * @return parsed Boolean or null when blank
     * @throws IllegalArgumentException if XPath evaluation fails
     */
    public static Boolean bool(final Node node, final String expression) {
        final String value = string(node, expression);
        return value == null ? null : Boolean.valueOf(value);
    }

    /**
     * <p>Checks whether a node is an element with the supplied local name.</p>
     * <p>The check proceeds as follows:</p>
     * <ol><li>Test the node type and compare its local name without checking namespace URI.</li></ol>
     * @param node node to inspect
     * @param localName expected local name
     * @return whether the node is an element with the expected local name
     */
    public static boolean elementNamed(final Node node, final String localName) {
        return node instanceof Element && localName.equals(node.getLocalName());
    }

    /**
     * <p>Escapes the five predefined XML special characters in a string.</p>
     * <p>Escaping proceeds as follows:</p>
     * <ol><li>Return an empty string for null; otherwise replace ampersands first,
     * then angle brackets, double quotes and apostrophes with entity references.</li></ol>
     * @param value text to escape, possibly null
     * @return escaped text, or an empty string for null
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
