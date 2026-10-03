package com.loomai.demo.dealership.provider;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/staff/provider-simulator")
public class ProviderSimulatorController {

    private final ProviderSimulatorService simulator;

    public ProviderSimulatorController(ProviderSimulatorService simulator) {
        this.simulator = simulator;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of("success", true, "simulator", simulator.status());
    }

    @PostMapping("/scenarios/{scenario}")
    public Map<String, Object> run(@PathVariable String scenario) {
        return Map.of("success", true, "receipt", simulator.run(scenario));
    }
}
