package com.loomai.verification.vehicleprovider.web;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.loomai.verification.vehicleprovider.model.SimulatorContracts.FaultMode;
import com.loomai.verification.vehicleprovider.model.SimulatorContracts.Profile;
import com.loomai.verification.vehicleprovider.model.SimulatorContracts.VehicleRecord;
import com.loomai.verification.vehicleprovider.service.SimulatorService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public-document conformance surface for the Auto Trader Connect stock contract.
 * Records are synthetic and this service is not affiliated with Auto Trader.
 */
@RestController
public class AutoTraderController {

    private final SimulatorService simulator;
    private final ProviderResponseSupport responseSupport;

    public AutoTraderController(SimulatorService simulator, ProviderResponseSupport responseSupport) {
        this.simulator = simulator;
        this.responseSupport = responseSupport;
    }

    @PostMapping(path = "/authenticate", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<ObjectNode> authenticate(@RequestParam String key, @RequestParam String secret) {
        return ResponseEntity.ok(simulator.authenticateAutoTrader(key, secret));
    }

    @GetMapping("/stock")
    public ResponseEntity<?> stock(
        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @RequestParam String advertiserId,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int pageSize,
        @RequestParam(required = false) String stockId,
        @RequestParam(required = false) String lifecycleState
    ) {
        String authenticatedAdvertiser = simulator.requireAutoTraderToken(authorization);
        simulator.requireAccount(Profile.AUTOTRADER, authenticatedAdvertiser, advertiserId);
        FaultMode fault = responseSupport.applyFault(Profile.AUTOTRADER, advertiserId);
        if (fault == FaultMode.MALFORMED_RESPONSE) {
            return malformed();
        }
        if (page < 1 || pageSize < 1 || pageSize > 200) {
            throw new IllegalArgumentException("page must start at 1 and pageSize must be between 1 and 200.");
        }

        List<VehicleRecord> eligible = simulator.vehicles(Profile.AUTOTRADER, advertiserId).stream()
            .filter(record -> !StringUtils.hasText(stockId) || record.id().equals(stockId.trim()))
            .filter(record -> !StringUtils.hasText(lifecycleState)
                || JsonProjection.lifecycleState(record).equalsIgnoreCase(lifecycleState.trim()))
            .toList();
        int from = Math.min((page - 1) * pageSize, eligible.size());
        int to = Math.min(from + pageSize, eligible.size());
        if (fault == FaultMode.PARTIAL_PAGE && to > from) {
            to = from + 1;
        }
        ArrayNode results = JsonProjection.autoTrader(eligible.subList(from, to));
        ObjectNode response = JsonProjection.object();
        response.set("results", results);
        response.put("totalResults", eligible.size());
        ResponseEntity.BodyBuilder builder = fault == FaultMode.PARTIAL_PAGE
            ? ResponseEntity.status(206).header("X-Simulator-Partial", "true")
            : ResponseEntity.ok();
        return builder.body(response);
    }

    private ResponseEntity<String> malformed() {
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Simulator-Request", simulator.newRequestId())
            .body("{\"results\":[{\"vehicle\":");
    }

}
