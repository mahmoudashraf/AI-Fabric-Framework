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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/profile-a")
public class ProfileAController {

    private final SimulatorService simulator;
    private final ProviderResponseSupport responseSupport;

    public ProfileAController(SimulatorService simulator, ProviderResponseSupport responseSupport) {
        this.simulator = simulator;
        this.responseSupport = responseSupport;
    }

    @PostMapping(path = "/authenticate", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<ObjectNode> authenticate(@RequestParam String key, @RequestParam String secret) {
        ObjectNode response = simulator.authenticateProfileA(key, secret);
        return withRequestId(ResponseEntity.ok(), response);
    }

    @GetMapping("/vehicles")
    public ResponseEntity<?> vehicles(
        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @RequestParam String account,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(name = "pageSize", defaultValue = "2") int pageSize
    ) {
        String authenticatedAccount = simulator.requireProfileAToken(authorization);
        simulator.requireAccount(Profile.PROFILE_A, authenticatedAccount, account);
        FaultMode fault = responseSupport.applyFault(Profile.PROFILE_A, account);
        if (fault == FaultMode.MALFORMED_RESPONSE) {
            return malformed();
        }
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("page must be positive and pageSize must be between 1 and 100.");
        }
        List<VehicleRecord> all = simulator.vehicles(Profile.PROFILE_A, account);
        int from = Math.min((page - 1) * pageSize, all.size());
        int to = Math.min(from + pageSize, all.size());
        if (fault == FaultMode.PARTIAL_PAGE && to > from) {
            to = from + 1;
        }
        ArrayNode items = JsonProjection.profileA(all.subList(from, to));
        ObjectNode result = JsonProjection.object()
            .put("accountId", account)
            .set("items", items);
        result.put("page", page)
            .put("pageSize", pageSize)
            .put("totalRecords", all.size())
            .put("totalPages", Math.max(1, (int) Math.ceil(all.size() / (double) pageSize)))
            .put("sourceVersion", simulator.sourceVersion(Profile.PROFILE_A, account))
            .put("fixtureVersion", simulator.status().fixture().fixtureVersion())
            .put("partial", fault == FaultMode.PARTIAL_PAGE);
        ResponseEntity.BodyBuilder builder = fault == FaultMode.PARTIAL_PAGE
            ? ResponseEntity.status(206).header("X-Simulator-Partial", "true")
            : ResponseEntity.ok();
        return withRequestId(builder, result);
    }

    @GetMapping("/vehicles/{vehicleId}")
    public ResponseEntity<?> vehicle(
        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @RequestParam String account,
        @PathVariable String vehicleId
    ) {
        String authenticatedAccount = simulator.requireProfileAToken(authorization);
        simulator.requireAccount(Profile.PROFILE_A, authenticatedAccount, account);
        FaultMode fault = responseSupport.applyFault(Profile.PROFILE_A, account);
        if (fault == FaultMode.MALFORMED_RESPONSE) {
            return malformed();
        }
        return withRequestId(ResponseEntity.ok(), JsonProjection.profileA(simulator.vehicle(Profile.PROFILE_A, account, vehicleId)));
    }

    private ResponseEntity<String> malformed() {
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Simulator-Request", simulator.newRequestId())
            .body("{\"items\":[{\"id\":");
    }

    private <T> ResponseEntity<T> withRequestId(ResponseEntity.BodyBuilder builder, T body) {
        return builder.header("X-Simulator-Request", simulator.newRequestId()).body(body);
    }
}
