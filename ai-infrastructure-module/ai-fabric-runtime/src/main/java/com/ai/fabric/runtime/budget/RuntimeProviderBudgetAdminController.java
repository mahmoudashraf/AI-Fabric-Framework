package com.ai.fabric.runtime.budget;

import com.ai.fabric.budget.BudgetException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(RuntimeProviderBudgetAdminController.PATH)
@ConditionalOnProperty(prefix = "loomai.provider-budget", name = "enabled", havingValue = "true")
public final class RuntimeProviderBudgetAdminController {
    public static final String PATH = "/api/admin/provider-budget";
    public static final String WRITE_SCOPE = "runtime:admin:provider-budget:write";
    private final RuntimeBudgetBindingService budgets;
    private final RuntimeBudgetAdminReadback administration;

    public RuntimeProviderBudgetAdminController(RuntimeBudgetBindingService budgets, RuntimeBudgetAdminReadback administration) {
        this.budgets = budgets; this.administration = administration;
    }

    @GetMapping
    public RuntimeBudgetAdminReadback.Readback read(HttpServletRequest request) { return administration.read(request); }

    @PostMapping("/initialize")
    public RuntimeBudgetBindingService.StoreReceipt initialize(HttpServletRequest request, @RequestBody RuntimeBudgetAllocation allocation) {
        administration.authorize(request, PATH + "/initialize", WRITE_SCOPE);
        return budgets.initialize(allocation);
    }

    @PostMapping("/activate")
    public RuntimeBudgetBindingService.StoreReceipt activate(HttpServletRequest request, @RequestBody RuntimeBudgetBindingService.Activation activation) {
        administration.authorize(request, PATH + "/activate", WRITE_SCOPE);
        return budgets.activate(activation);
    }

    @ExceptionHandler(RuntimeBudgetBindingException.class)
    public ResponseEntity<Map<String, String>> bindingFailure(RuntimeBudgetBindingException failure) {
        HttpStatus status = switch (failure.code()) {
            case ALLOCATION_SCOPE_MISMATCH -> HttpStatus.FORBIDDEN;
            case ALLOCATION_FINGERPRINT_MISMATCH -> HttpStatus.BAD_REQUEST;
            case STORAGE_UNAVAILABLE, DISABLED -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(status).body(Map.of("code", "RUNTIME_BUDGET_" + failure.code().name()));
    }

    @ExceptionHandler(BudgetException.class)
    public ResponseEntity<Map<String, String>> ledgerFailure(BudgetException failure) {
        return ResponseEntity.status(failure.code() == BudgetException.Code.STORAGE_UNAVAILABLE
            ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.CONFLICT).body(Map.of("code", "PROVIDER_BUDGET_" + failure.code().name()));
    }
}
