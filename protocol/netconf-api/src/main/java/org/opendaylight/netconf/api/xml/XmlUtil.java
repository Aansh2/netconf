/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.xml;

import static javax.xml.XMLConstants.XMLNS_ATTRIBUTE;
import static javax.xml.XMLConstants.XMLNS_ATTRIBUTE_NS_URI;

import com.google.common.io.Resources;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

public final class XmlUtil {
    /**
     * A pre-compiled XSL template to deal with Java XML transform creating empty lines when indenting is enabled, as
     * detailed in <a href="https://bugs.openjdk.org/browse/JDK-8262285">JDK-8262285</a>.
     */
    private static final Templates PRETTY_PRINT_TEMPLATE;

    static {
        try {
            PRETTY_PRINT_TEMPLATE = TransformerFactory.newInstance()
                .newTemplates(new StreamSource(Resources.getResource(XmlUtil.class, "/pretty-print.xsl").openStream()));
        } catch (IOException | TransformerConfigurationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static final DocumentBuilderFactory BUILDER_FACTORY;

    static {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            // Performance improvement for messages with size <10k according to
            // https://xerces.apache.org/xerces2-j/faq-performance.html
            factory.setFeature("http://apache.org/xml/features/dom/defer-node-expansion", false);
        } catch (final ParserConfigurationException e) {
            throw new ExceptionInInitializerError(e);
        }
        factory.setNamespaceAware(true);
        factory.setCoalescing(true);
        factory.setIgnoringElementContentWhitespace(true);
        factory.setIgnoringComments(true);
        BUILDER_FACTORY = factory;
    }

    private static final ThreadLocal<DocumentBuilder> DEFAULT_DOM_BUILDER = new ThreadLocal<>() {
        @Override
        protected DocumentBuilder initialValue() {
            try {
                return BUILDER_FACTORY.newDocumentBuilder();
            } catch (final ParserConfigurationException e) {
                throw new IllegalStateException("Failed to create threadLocal dom builder", e);
            }
        }

        @Override
        public void set(final DocumentBuilder value) {
            throw new UnsupportedOperationException();
        }
    };

    private XmlUtil() {
        // Hidden on purpose
    }

    public static Element readXmlToElement(final File xmlFile) throws SAXException, IOException {
        return readXmlToDocument(new FileInputStream(xmlFile)).getDocumentElement();
    }

    public static Element readXmlToElement(final String xmlContent) throws SAXException, IOException {
        Document doc = readXmlToDocument(xmlContent);
        return doc.getDocumentElement();
    }

    public static Element readXmlToElement(final InputStream xmlContent) throws SAXException, IOException {
        Document doc = readXmlToDocument(xmlContent);
        return doc.getDocumentElement();
    }

    public static Document readXmlToDocument(final String xmlContent) throws SAXException, IOException {
        return readXmlToDocument(new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8)));
    }

    // TODO improve exceptions throwing
    // along with XmlElement

    public static Document readXmlToDocument(final InputStream xmlContent) throws SAXException, IOException {
        Document doc = DEFAULT_DOM_BUILDER.get().parse(xmlContent);

        doc.getDocumentElement().normalize();
        return doc;
    }

    public static Document newDocument() {
        return DEFAULT_DOM_BUILDER.get().newDocument();
    }

    public static Element createElement(final Document document, final String qname,
            final Optional<String> namespaceURI) {
        if (namespaceURI.isEmpty()) {
            return document.createElement(qname);
        }

        final String uri = namespaceURI.orElseThrow();
        final Element element = document.createElementNS(uri, qname);
        String name = XMLNS_ATTRIBUTE;
        if (element.getPrefix() != null) {
            name += ":" + element.getPrefix();
        }
        element.setAttributeNS(XMLNS_ATTRIBUTE_NS_URI, name, uri);
        return element;
    }

    public static Element createTextElement(final Document document, final String qname, final String content,
            final Optional<String> namespaceURI) {
        Element typeElement = createElement(document, qname, namespaceURI);
        typeElement.appendChild(document.createTextNode(content));
        return typeElement;
    }

    public static Element createTextElementWithNamespacedContent(final Document document, final String qname,
            final String prefix, final String namespace, final String contentWithoutPrefix) {

        return createTextElementWithNamespacedContent(document, qname, prefix, namespace, contentWithoutPrefix,
                Optional.empty());
    }

    public static Element createTextElementWithNamespacedContent(final Document document, final String qname,
            final String prefix, final String namespace, final String contentWithoutPrefix,
            final Optional<String> namespaceURI) {

        String content = createPrefixedValue(XmlNetconfConstants.PREFIX, contentWithoutPrefix);
        Element element = createTextElement(document, qname, content, namespaceURI);
        String prefixedNamespaceAttr = createPrefixedValue(XMLNS_ATTRIBUTE, prefix);
        element.setAttributeNS(XMLNS_ATTRIBUTE_NS_URI, prefixedNamespaceAttr, namespace);
        return element;
    }

    public static String createPrefixedValue(final String prefix, final String value) {
        return prefix + ":" + value;
    }

    /**
     * Return a new {@link Transformer} which performs indentation.
     *
     * @return A new Transformer
     * @throws TransformerConfigurationException if a Transformer can not be created
     */
    public static Transformer newIndentingTransformer() throws TransformerConfigurationException {
        final Transformer ret = PRETTY_PRINT_TEMPLATE.newTransformer();
        ret.setOutputProperty(OutputKeys.INDENT, "yes");
        return ret;
    }

    public static String toString(final Document document) {
        return toString(document.getDocumentElement());
    }

    public static String toString(final Element xml) {
        return toString(xml, false);
    }

    public static String toString(final XmlElement xmlElement) {
        return toString(xmlElement.getDomElement(), false);
    }

    public static String toString(final Element xml, final boolean addXmlDeclaration) {
        final StringWriter writer = new StringWriter();

        try {
            Transformer transformer = newIndentingTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, addXmlDeclaration ? "no" : "yes");
            transformer.transform(new DOMSource(xml), new StreamResult(writer));
        } catch (TransformerException e) {
            throw new IllegalStateException("Unable to serialize xml element " + xml, e);
        }

        return writer.toString();
    }

    public static String toString(final Document doc, final boolean addXmlDeclaration) {
        return toString(doc.getDocumentElement(), addXmlDeclaration);
    }

    public static Object evaluateXPath(final XPathExpression expr, final Object rootNode, final QName returnType) {
        try {
            return expr.evaluate(rootNode, returnType);
        } catch (final XPathExpressionException e) {
            throw new IllegalStateException("Error while evaluating xpath expression " + expr, e);
        }
    }

    public static Document createDocumentCopy(final Document original) {
        final Document copiedDocument = newDocument();
        final Node copiedRoot = copiedDocument.importNode(original.getDocumentElement(), true);
        copiedDocument.appendChild(copiedRoot);
        return copiedDocument;
    }
}
