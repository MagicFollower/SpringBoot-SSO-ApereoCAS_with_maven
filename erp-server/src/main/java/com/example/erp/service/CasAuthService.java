package com.example.erp.service;

import com.example.erp.config.ErpProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 调用 CAS /p3/serviceValidate 校验服务票据。
 */
@Service
@RequiredArgsConstructor
public class CasAuthService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CasAuthService.class);

    private final ErpProperties properties;

    public CasValidationResult validate(final String ticket, final String serviceUrl) {
        if (ticket == null || ticket.isBlank()) {
            return CasValidationResult.fail("缺少 ticket");
        }
        try {
            final String validateUrl = properties.getCas().getBaseUrl()
                    + properties.getCas().getValidatePath()
                    + "?service=" + encode(serviceUrl)
                    + "&ticket=" + encode(ticket);
            final HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(properties.getCas().getHttpTimeoutSeconds()))
                    .build();
            final HttpRequest request = HttpRequest.newBuilder(URI.create(validateUrl))
                    .timeout(Duration.ofSeconds(properties.getCas().getHttpTimeoutSeconds()))
                    .GET()
                    .build();
            final HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                return CasValidationResult.fail("CAS 校验返回 HTTP " + response.statusCode());
            }
            return parse(response.body());
        } catch (final Exception e) {
            LOGGER.warn("CAS ticket validation failed for service [{}]: {}", serviceUrl, e.getMessage());
            return CasValidationResult.fail("CAS 校验请求失败：" + e.getMessage());
        }
    }

    private CasValidationResult parse(final String xml) {
        try {
            final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            final Document document = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            final Node user = firstElement(document, "user");
            if (user == null || user.getTextContent().isBlank()) {
                final Node failure = firstElement(document, "authenticationFailure");
                final String message = failure == null ? "CAS 未返回用户信息"
                        : failure.getTextContent().trim();
                return CasValidationResult.fail(message);
            }
            return CasValidationResult.ok(user.getTextContent().trim());
        } catch (final Exception e) {
            LOGGER.warn("Unable to parse CAS validation response: {}", e.getMessage());
            return CasValidationResult.fail("CAS 校验响应解析失败");
        }
    }

    private static Node firstElement(final Document document, final String localName) {
        final NodeList nodes = document.getElementsByTagNameNS("*", localName);
        return nodes.getLength() == 0 ? null : nodes.item(0);
    }

    private static String encode(final String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}