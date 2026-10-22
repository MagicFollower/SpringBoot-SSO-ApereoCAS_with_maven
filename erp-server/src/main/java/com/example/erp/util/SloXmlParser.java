package com.example.erp.util;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/**
 * 解析 CAS SLO 的 SAML LogoutRequest，提取 SessionIndex。
 */
public final class SloXmlParser {

    private SloXmlParser() {
    }

    public static String extractSessionIndex(final String logoutRequest) {
        if (logoutRequest == null || logoutRequest.isBlank()) {
            return null;
        }
        try {
            final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            final Document document = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(logoutRequest.getBytes(StandardCharsets.UTF_8)));
            final NodeList nodes = document.getElementsByTagNameNS("*", "SessionIndex");
            if (nodes.getLength() == 0) {
                return null;
            }
            final Node node = nodes.item(0);
            return node.getTextContent() == null ? null : node.getTextContent().trim();
        } catch (final Exception e) {
            return null;
        }
    }
}