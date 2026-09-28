package com.loomai.verification.vehicleprovider.web;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.loomai.verification.vehicleprovider.model.SimulatorContracts.FaultMode;
import com.loomai.verification.vehicleprovider.model.SimulatorContracts.Profile;
import com.loomai.verification.vehicleprovider.model.SimulatorContracts.VehicleRecord;
import com.loomai.verification.vehicleprovider.service.SimulatorService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

@RestController
@RequestMapping("/api/profile-b/accounts/{ownerRef}")
public class ProfileBController {

    private final SimulatorService simulator;
    private final ProviderResponseSupport responseSupport;

    public ProfileBController(SimulatorService simulator, ProviderResponseSupport responseSupport) {
        this.simulator = simulator;
        this.responseSupport = responseSupport;
    }

    @GetMapping("/vehicles")
    public ResponseEntity<?> vehicles(
        @PathVariable String ownerRef,
        @RequestHeader(name = "X-Simulator-Api-Key", required = false) String apiKey,
        @RequestParam(required = false) String cursor,
        @RequestParam(defaultValue = "2") int limit
    ) {
        String authenticatedAccount = simulator.requireProfileBApiKey(apiKey);
        simulator.requireAccount(Profile.PROFILE_B, authenticatedAccount, ownerRef);
        FaultMode fault = responseSupport.applyFault(Profile.PROFILE_B, ownerRef);
        if (fault == FaultMode.MALFORMED_RESPONSE) {
            return malformed();
        }
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100.");
        }
        List<VehicleRecord> all = simulator.vehicles(Profile.PROFILE_B, ownerRef);
        CursorState cursorState = decodeCursor(cursor);
        List<VehicleRecord> eligible = cursorState.mode() == CursorMode.SNAPSHOT
            ? all
            : all.stream().filter(record -> record.version() > cursorState.baselineVersion()).toList();
        int offset = cursorState.offset();
        if (offset > eligible.size()) {
            throw new IllegalArgumentException("cursor points beyond the current result set.");
        }
        int to = Math.min(offset + limit, eligible.size());
        if (fault == FaultMode.PARTIAL_PAGE && to > offset) {
            to = offset + 1;
        }
        ArrayNode vehicles = JsonProjection.profileB(eligible.subList(offset, to));
        long currentRevision = simulator.sourceVersion(Profile.PROFILE_B, ownerRef);
        String nextCursor = nextCursor(cursorState, to, eligible.size(), currentRevision);
        ObjectNode paging = JsonProjection.object().put("returned", vehicles.size());
        paging.put("nextCursor", nextCursor);
        ObjectNode data = JsonProjection.object()
            .put("ownerRef", ownerRef)
            .set("vehicles", vehicles);
        ObjectNode result = JsonProjection.object();
        result.set("data", data);
        result.set("paging", paging);
        result.put("revision", currentRevision)
            .put("fixtureVersion", simulator.status().fixture().fixtureVersion())
            .put("partial", fault == FaultMode.PARTIAL_PAGE);
        ResponseEntity.BodyBuilder builder = fault == FaultMode.PARTIAL_PAGE
            ? ResponseEntity.status(206).header("X-Simulator-Partial", "true")
            : ResponseEntity.ok();
        return withRequestId(builder, result);
    }

    @GetMapping("/vehicles/{vehicleId}")
    public ResponseEntity<?> vehicle(
        @PathVariable String ownerRef,
        @PathVariable String vehicleId,
        @RequestHeader(name = "X-Simulator-Api-Key", required = false) String apiKey
    ) {
        String authenticatedAccount = simulator.requireProfileBApiKey(apiKey);
        simulator.requireAccount(Profile.PROFILE_B, authenticatedAccount, ownerRef);
        FaultMode fault = responseSupport.applyFault(Profile.PROFILE_B, ownerRef);
        if (fault == FaultMode.MALFORMED_RESPONSE) {
            return malformed();
        }
        return withRequestId(ResponseEntity.ok(), JsonProjection.profileB(simulator.vehicle(Profile.PROFILE_B, ownerRef, vehicleId)));
    }

    private CursorState decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return new CursorState(CursorMode.SNAPSHOT, 0, 0);
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = decoded.split(":", -1);
            if (parts.length == 2 && "resume".equals(parts[0])) {
                return new CursorState(CursorMode.RESUME, Long.parseLong(parts[1]), 0);
            }
            if (parts.length == 3 && "snapshot".equals(parts[0])) {
                return new CursorState(CursorMode.SNAPSHOT, Long.parseLong(parts[1]), Integer.parseInt(parts[2]));
            }
            if (parts.length == 3 && "delta".equals(parts[0])) {
                return new CursorState(CursorMode.DELTA, Long.parseLong(parts[1]), Integer.parseInt(parts[2]));
            }
            throw new IllegalArgumentException("invalid cursor shape");
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("cursor is invalid.");
        }
    }

    private String nextCursor(CursorState current, int nextOffset, int eligibleSize, long currentRevision) {
        if (nextOffset < eligibleSize) {
            String mode = current.mode() == CursorMode.SNAPSHOT ? "snapshot" : "delta";
            long baseline = current.mode() == CursorMode.SNAPSHOT ? currentRevision : current.baselineVersion();
            return encodeCursor(mode + ":" + baseline + ":" + nextOffset);
        }
        return encodeCursor("resume:" + currentRevision);
    }

    private String encodeCursor(String value) {
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private ResponseEntity<String> malformed() {
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Simulator-Request", simulator.newRequestId())
            .body("{\"data\":{\"vehicles\":[");
    }

    private <T> ResponseEntity<T> withRequestId(ResponseEntity.BodyBuilder builder, T body) {
        return builder.header("X-Simulator-Request", simulator.newRequestId()).body(body);
    }

    private enum CursorMode {
        SNAPSHOT,
        RESUME,
        DELTA
    }

    private record CursorState(CursorMode mode, long baselineVersion, int offset) {
    }
}
