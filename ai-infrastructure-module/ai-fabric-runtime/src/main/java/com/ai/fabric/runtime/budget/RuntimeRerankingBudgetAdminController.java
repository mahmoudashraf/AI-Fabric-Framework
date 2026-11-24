package com.ai.fabric.runtime.budget;

import com.ai.fabric.runtime.auth.RuntimeRequestAuthResolver;
import com.ai.fabric.budget.BudgetException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Uses the existing private authority and immutable allocation/activation lifecycle, not chat credentials. */
@RestController
@RequestMapping("/api/admin/reranking-budget")
@ConditionalOnProperty(prefix = "ai.infrastructure.rag.reranking", name = "enabled", havingValue = "true")
public class RuntimeRerankingBudgetAdminController {
    private final RuntimeBudgetBindingService budgets;
    private final RuntimeBudgetAdminReadback administration;

    public RuntimeRerankingBudgetAdminController(@Qualifier("rerankingBudgetBinding") RuntimeBudgetBindingService budgets,
                                                RuntimeRequestAuthResolver auth) {
        this.budgets = budgets;
        this.administration = new RuntimeBudgetAdminReadback(budgets, auth);
    }

    @GetMapping
    public RuntimeBudgetAdminReadback.Readback read(HttpServletRequest request) { return administration.read(request); }

    @PostMapping("/initialize")
    public RuntimeBudgetBindingService.StoreReceipt initialize(HttpServletRequest request, @RequestBody RuntimeBudgetAllocation allocation) {
        administration.authorize(request, request.getRequestURI(), RuntimeProviderBudgetAdminController.WRITE_SCOPE);
        return budgets.initialize(allocation);
    }

    @PostMapping("/activate")
    public RuntimeBudgetBindingService.StoreReceipt activate(HttpServletRequest request,
                                                             @RequestBody RuntimeBudgetBindingService.Activation activation) {
        administration.authorize(request, request.getRequestURI(), RuntimeProviderBudgetAdminController.WRITE_SCOPE);
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
        HttpStatus status = failure.code() == BudgetException.Code.STORAGE_UNAVAILABLE ? HttpStatus.SERVICE_UNAVAILABLE
            : failure.code() == BudgetException.Code.INVALID_REQUEST ? HttpStatus.BAD_REQUEST : HttpStatus.CONFLICT;
        return ResponseEntity.status(status).body(Map.of("code", "PROVIDER_BUDGET_" + failure.code().name()));
    }
}
