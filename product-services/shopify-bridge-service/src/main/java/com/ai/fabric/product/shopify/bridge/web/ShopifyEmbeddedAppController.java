package com.ai.fabric.product.shopify.bridge.web;

import com.ai.fabric.product.shopify.bridge.config.ShopifyBridgeProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Controller
public class ShopifyEmbeddedAppController {

    private final Path merchantUiDirectory;
    private final ShopifyBridgeProperties properties;

    public ShopifyEmbeddedAppController(@Value("${shopify.bridge.merchant-ui-directory:}") String merchantUiDirectory,
                                        ShopifyBridgeProperties properties) {
        this.merchantUiDirectory = merchantUiDirectory == null || merchantUiDirectory.isBlank()
            ? null
            : Path.of(merchantUiDirectory).normalize();
        this.properties = properties;
    }

    @GetMapping(value = {"/", "/app"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Resource> index() {
        Resource resource = renderIndexHtml();
        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_HTML)
            .body(resource);
    }

    @GetMapping("/assets/{*assetPath}")
    public ResponseEntity<Resource> asset(@PathVariable String assetPath) {
        String normalizedAssetPath = assetPath.startsWith("/") ? assetPath.substring(1) : assetPath;
        Resource resource = resolveFile("assets/" + normalizedAssetPath);
        MediaType contentType = MediaTypeFactory.getMediaType(resource)
            .orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
            .contentType(contentType)
            .body(resource);
    }

    private Resource renderIndexHtml() {
        Path indexPath = resolvePath("index.html");
        try {
            String html = Files.readString(indexPath, StandardCharsets.UTF_8);
            return new ByteArrayResource(injectRuntimeShopifyApiKey(html).getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new ResponseStatusException(NOT_FOUND, "Shopify embedded app asset not found.");
        }
    }

    private Resource resolveFile(String relativePath) {
        return new FileSystemResource(resolvePath(relativePath));
    }

    private Path resolvePath(String relativePath) {
        if (merchantUiDirectory == null) {
            throw new ResponseStatusException(NOT_FOUND, "Shopify embedded app UI is not configured.");
        }
        Path candidate = merchantUiDirectory.resolve(relativePath).normalize();
        if (!candidate.startsWith(merchantUiDirectory) || !Files.exists(candidate) || !Files.isRegularFile(candidate)) {
            throw new ResponseStatusException(NOT_FOUND, "Shopify embedded app asset not found.");
        }
        return candidate;
    }

    private String injectRuntimeShopifyApiKey(String html) {
        String apiKey = properties.shopifyApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return html;
        }
        String metaTag = "<meta name=\"shopify-api-key\" content=\"" + escapeHtmlAttribute(apiKey) + "\" />";
        if (html.contains("meta name=\"shopify-api-key\"")) {
            return html.replaceAll(
                "<meta\\s+name=\"shopify-api-key\"\\s+content=\"[^\"]*\"\\s*/?>",
                Matcher.quoteReplacement(metaTag)
            );
        }
        String appBridgeScript = "<script src=\"https://cdn.shopify.com/shopifycloud/app-bridge.js\"></script>";
        int appBridgeIndex = html.indexOf(appBridgeScript);
        if (appBridgeIndex >= 0) {
            return html.substring(0, appBridgeIndex) + "    " + metaTag + "\n" + html.substring(appBridgeIndex);
        }
        int headEnd = html.indexOf("</head>");
        if (headEnd < 0) {
            return metaTag + html;
        }
        return html.substring(0, headEnd) + "    " + metaTag + "\n" + html.substring(headEnd);
    }

    private String escapeHtmlAttribute(String value) {
        return value
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
    }
}
