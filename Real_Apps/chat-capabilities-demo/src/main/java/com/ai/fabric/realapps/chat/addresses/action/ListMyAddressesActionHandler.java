package com.ai.fabric.realapps.chat.addresses.action;

import com.ai.fabric.realapps.chat.addresses.domain.Address;
import com.ai.fabric.realapps.chat.addresses.service.AddressService;
import com.ai.infrastructure.intent.action.AIActionMetaData;
import com.ai.infrastructure.intent.action.ActionHandler;
import com.ai.infrastructure.intent.action.ActionResult;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
@Slf4j
public class ListMyAddressesActionHandler implements ActionHandler {

    private final AddressService addressService;

    @Override
    public AIActionMetaData getActionMetadata() {
        return AIActionMetaData.builder()
            .name("list_my_addresses")
            .description("List my saved shipping/billing addresses")
            .category("commerce")
            .parameters(Map.of(
                "limit", "Max number of addresses to return (optional; default 20)"
            ))
            .build();
    }

    @Override
    public boolean validateActionAllowed(String userId) {
        return StringUtils.hasText(userId);
    }

    @Override
    public String getConfirmationMessage(Map<String, Object> params) {
        return "Fetch your saved addresses?";
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }

    @Override
    public ActionResult executeAction(Map<String, Object> params, String userId) {
        int limit = intParam(params, "limit", 20);
        List<Address> addresses = addressService.listForUser(userId, limit);

        List<Map<String, Object>> payload = addresses.stream()
            .filter(a -> a != null)
            .map(a -> Map.<String, Object>of(
                "addressId", a.getId(),
                "type", a.getType() != null ? a.getType().name() : null,
                "line1", a.getLine1(),
                "line2", a.getLine2(),
                "city", a.getCity(),
                "state", a.getState(),
                "postalCode", a.getPostalCode(),
                "country", a.getCountry(),
                "isDefault", a.isDefault()
            ))
            .toList();

        return ActionResult.builder()
            .success(true)
            .message(payload.isEmpty() ? "No saved addresses found" : "Saved addresses")
            .data(Map.of(
                "count", payload.size(),
                "addresses", payload
            ))
            .build();
    }

    @Override
    public ActionResult handleError(Exception e, String userId) {
        log.error("List my addresses failed for user {}", userId, e);
        return ActionResult.builder()
            .success(false)
            .message("Failed to fetch addresses: " + e.getMessage())
            .errorCode("LIST_MY_ADDRESSES_FAILED")
            .build();
    }

    private int intParam(Map<String, Object> params, String key, int defaultValue) {
        Object raw = params != null ? params.get(key) : null;
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw != null) {
            try {
                return Integer.parseInt(raw.toString());
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }
}

