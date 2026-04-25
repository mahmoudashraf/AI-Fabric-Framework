package com.ai.fabric.platform.backend.partner.gateway;

import com.ai.fabric.platform.backend.partner.model.PartnerCatalogEntrySummary;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class StaticPartnerCatalogSource implements PartnerCatalogSource {

    @Override
    public List<PartnerCatalogEntrySummary> listCatalog() {
        return List.of(
            entry("ai-search", "AI search", "Free", "Search", "Shoppers can ask for product discovery help from real store content.", "Search dock or launcher", List.of("products", "collections", "policies"), List.of("Install Companion", "Enable the app embed", "Run Knowledge Sync"), List.of("Ask a product discovery question", "Confirm the answer links to store products", "Confirm Free exposes AI search only"), "Grounded search answer with product links.", List.of("No product result", "Outdated policy answer"), List.of("Does not complete checkout or support cases."), "AI search helps shoppers discover products using your store catalog and policies.", List.of("query text", "storefront screenshot", "verification result")),
            entry("product-insight", "Product insight block", "Starter", "Product page", "Shoppers get grounded product details on product pages.", "Product information section", List.of("products", "metafields", "reviews"), List.of("Place product insight block", "Confirm product source coverage"), List.of("Open product page", "Verify product insight renders", "Open depth layer from the surface"), "Product-specific insight renders without order/account claims.", List.of("Missing block", "Generic answer"), List.of("Read-only; does not add to cart."), "Product insight adds grounded product-page guidance.", List.of("product URL", "surface screenshot")),
            entry("product-faq", "Product FAQ", "Starter", "Product page", "Common product questions can be answered inline.", "FAQ app block", List.of("products", "pages", "policies"), List.of("Place FAQ prompts", "Sync product and policy sources"), List.of("Open FAQ prompts", "Ask sizing, material, or care question"), "FAQ answer cites store content or hands off safely.", List.of("No answer", "Unsupported claim"), List.of("Read-only; no account-specific support."), "Product FAQ answers common product questions from store content.", List.of("question", "answer screenshot")),
            entry("comparison", "Comparison", "Starter", "Product discovery", "Shoppers compare candidate products without leaving the storefront.", "Comparison block or Max Mode depth layer", List.of("products", "collections", "metafields"), List.of("Enable comparison placement", "Confirm products have comparable attributes"), List.of("Ask for a comparison", "Confirm compared products come from the store"), "Comparison response stays product-focused and read-only.", List.of("Products not comparable", "Missing attributes"), List.of("Does not choose for the shopper or complete checkout."), "Comparison helps shoppers evaluate products using store data.", List.of("comparison query", "result screenshot")),
            entry("policy-strip", "Policy strip", "Starter", "Product/cart context", "Shoppers see shipping, returns, and policy answers in context.", "Policy strip block", List.of("policies", "pages"), List.of("Enable policy sources", "Place policy strip"), List.of("Ask shipping or returns question", "Confirm policy wording is grounded"), "Policy answer is clear and store-specific.", List.of("Missing policies", "Outdated policy"), List.of("Does not initiate returns or refunds."), "Policy strip gives grounded answers to common policy questions.", List.of("policy source status", "answer screenshot")),
            entry("contextual-pill", "Contextual pill", "Starter", "Storefront pages", "Shoppers can open relevant guidance from page context.", "Floating contextual pill", List.of("products", "collections", "pages"), List.of("Enable contextual pill", "Verify page context"), List.of("Open a product page", "Click contextual pill", "Confirm depth layer opens"), "Pill opens read-only Companion depth layer with page context.", List.of("Pill hidden", "Depth layer lacks context"), List.of("No governed actions in Starter."), "Contextual pill gives page-aware shopper guidance.", List.of("page URL", "pill screenshot")),
            entry("read-only-chat", "Read-only Companion chat", "Starter", "Depth layer", "Shoppers can ask deeper questions without leaving the product context.", "Max Mode/depth layer", List.of("products", "collections", "policies", "pages", "articles", "metaobjects"), List.of("Enable Starter surfaces", "Run Knowledge Sync", "Confirm support handoff"), List.of("Open Max Mode from a surface", "Ask a deep product/policy question", "Confirm no order lookup appears"), "Depth layer answers read-only questions and hands off account/order cases.", List.of("Legacy chat shell", "Order lookup claim"), List.of("Starter excludes order lookup and governed actions."), "Read-only Companion chat gives deeper grounded store intelligence.", List.of("Max Mode screenshot", "verification pack output"))
        );
    }

    private PartnerCatalogEntrySummary entry(String surfaceId,
                                             String name,
                                             String tier,
                                             String type,
                                             String shopperProblem,
                                             String storefrontPlacement,
                                             List<String> requiredSourceData,
                                             List<String> merchantSetup,
                                             List<String> verificationSteps,
                                             String healthyResult,
                                             List<String> failureSigns,
                                             List<String> limitations,
                                             String launchSafeClaim,
                                             List<String> escalationEvidence) {
        return new PartnerCatalogEntrySummary(
            surfaceId,
            name,
            tier,
            type,
            shopperProblem,
            storefrontPlacement,
            requiredSourceData,
            merchantSetup,
            verificationSteps,
            healthyResult,
            failureSigns,
            limitations,
            launchSafeClaim,
            escalationEvidence
        );
    }
}
