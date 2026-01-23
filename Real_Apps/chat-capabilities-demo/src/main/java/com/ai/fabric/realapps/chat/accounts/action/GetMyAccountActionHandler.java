package com.ai.fabric.realapps.chat.accounts.action;

import com.ai.fabric.realapps.chat.accounts.domain.Account;
import com.ai.fabric.realapps.chat.accounts.service.AccountService;
import com.ai.infrastructure.intent.action.AIActionMetaData;
import com.ai.infrastructure.intent.action.ActionHandler;
import com.ai.infrastructure.intent.action.ActionResult;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
@Slf4j
public class GetMyAccountActionHandler implements ActionHandler {

    private final AccountService accountService;

    @Override
    public AIActionMetaData getActionMetadata() {
        return AIActionMetaData.builder()
            .name("get_my_account")
            .description("Get my account/profile details")
            .category("commerce")
            .build();
    }

    @Override
    public boolean validateActionAllowed(String userId) {
        return StringUtils.hasText(userId);
    }

    @Override
    public String getConfirmationMessage(Map<String, Object> params) {
        return "Fetch your account details?";
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }

    @Override
    public ActionResult executeAction(Map<String, Object> params, String userId) {
        Account account = accountService.findByUserId(userId).orElse(null);
        if (account == null) {
            return ActionResult.builder()
                .success(true)
                .message("No account profile found")
                .data(Map.of("userId", userId))
                .build();
        }
        return ActionResult.builder()
            .success(true)
            .message("Account details")
            .data(Map.of(
                "accountId", account.getId(),
                "userId", account.getUserId(),
                "name", account.getName(),
                "email", account.getEmail(),
                "tier", account.getTier(),
                "segment", account.getSegment()
            ))
            .build();
    }

    @Override
    public ActionResult handleError(Exception e, String userId) {
        log.error("Get my account failed for user {}", userId, e);
        return ActionResult.builder()
            .success(false)
            .message("Failed to fetch account: " + e.getMessage())
            .errorCode("GET_MY_ACCOUNT_FAILED")
            .build();
    }
}

