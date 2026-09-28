package com.loomai.verification.vehicleprovider.web;

import com.loomai.verification.vehicleprovider.model.SimulatorContracts.ControlStatus;
import com.loomai.verification.vehicleprovider.model.SimulatorContracts.EventDelivery;
import com.loomai.verification.vehicleprovider.model.SimulatorContracts.EventRequest;
import com.loomai.verification.vehicleprovider.model.SimulatorContracts.FaultRequest;
import com.loomai.verification.vehicleprovider.model.SimulatorContracts.FixtureRun;
import com.loomai.verification.vehicleprovider.model.SimulatorContracts.MutationReceipt;
import com.loomai.verification.vehicleprovider.model.SimulatorContracts.Profile;
import com.loomai.verification.vehicleprovider.model.SimulatorContracts.VehicleInput;
import com.loomai.verification.vehicleprovider.service.SimulatorService;
import com.loomai.verification.vehicleprovider.service.WebhookEmitter;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/control")
public class ControlController {

    private final SimulatorService simulator;
    private final WebhookEmitter webhookEmitter;

    public ControlController(SimulatorService simulator, WebhookEmitter webhookEmitter) {
        this.simulator = simulator;
        this.webhookEmitter = webhookEmitter;
    }

    @GetMapping("/status")
    public ControlStatus status() {
        return simulator.status();
    }

    @PostMapping("/reset")
    public FixtureRun reset() {
        return simulator.reset();
    }

    @PutMapping("/accounts/{profile}/{accountId}/vehicles/{vehicleId}")
    public MutationReceipt upsert(
        @PathVariable String profile,
        @PathVariable String accountId,
        @PathVariable String vehicleId,
        @Valid @RequestBody VehicleInput input
    ) {
        if (!vehicleId.equals(input.id())) {
            throw new IllegalArgumentException("Path and payload vehicle identifiers must match.");
        }
        return simulator.upsert(Profile.parse(profile), accountId, input);
    }

    @DeleteMapping("/accounts/{profile}/{accountId}/vehicles/{vehicleId}")
    public MutationReceipt delete(
        @PathVariable String profile,
        @PathVariable String accountId,
        @PathVariable String vehicleId,
        @RequestParam(defaultValue = "false") boolean purge
    ) {
        return simulator.delete(Profile.parse(profile), accountId, vehicleId, purge);
    }

    @PutMapping("/accounts/{profile}/{accountId}/fault")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void fault(
        @PathVariable String profile,
        @PathVariable String accountId,
        @Valid @RequestBody FaultRequest request
    ) {
        simulator.configureFault(Profile.parse(profile), accountId, request.mode(), request.remaining(), request.delayMs());
    }

    @PostMapping("/accounts/{profile}/{accountId}/events")
    public EventDelivery event(
        @PathVariable String profile,
        @PathVariable String accountId,
        @Valid @RequestBody EventRequest request
    ) {
        return webhookEmitter.emit(Profile.parse(profile), accountId, request);
    }
}
