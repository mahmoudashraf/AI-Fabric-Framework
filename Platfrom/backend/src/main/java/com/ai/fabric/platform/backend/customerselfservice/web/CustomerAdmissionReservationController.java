package com.ai.fabric.platform.backend.customerselfservice.web;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerAdmissionReservationContracts.Request;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerAdmissionReservationContracts.Summary;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerAdmissionReservationService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferCodec;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/customer-self-service/catalogue/{offerId}/deployments/{deploymentId}/admission-reservations")
@PreAuthorize("hasRole('CUSTOMER_ADMIN')")
public class CustomerAdmissionReservationController {
    private final CustomerAdmissionReservationService reservations;
    private final CustomerOfferCodec codec;

    public CustomerAdmissionReservationController(CustomerAdmissionReservationService reservations, CustomerOfferCodec codec) {
        this.reservations = reservations;
        this.codec = codec;
    }

    @PostMapping
    public Summary reserve(@PathVariable String offerId, @PathVariable String deploymentId,
                           @RequestBody JsonNode body) {
        return reservations.reserve(offerId, deploymentId, codec.read(codec.write(body), Request.class));
    }

    @GetMapping
    public List<Summary> list(@PathVariable String offerId, @PathVariable String deploymentId,
                              @RequestParam String targetProfileId) {
        return reservations.list(offerId, deploymentId, targetProfileId);
    }

    @GetMapping("/{reservationId}")
    public Summary status(@PathVariable String offerId, @PathVariable String deploymentId,
                          @PathVariable String reservationId) {
        return reservations.status(offerId, deploymentId, reservationId);
    }
}
