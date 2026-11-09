package com.loomai.verification.vehicleprovider.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loomai.verification.vehicleprovider.config.SimulatorProperties;
import com.loomai.verification.vehicleprovider.model.SimulatorContracts.FixtureRun;
import com.loomai.verification.vehicleprovider.persistence.SimulatorRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class SimulatorServiceFixtureInitializationTest {

    @Test
    void matchingPersistedFixtureVersionDoesNotResetOnStartup() {
        SimulatorProperties properties = mock(SimulatorProperties.class);
        when(properties.fixtureVersion()).thenReturn("external-vehicle-provider-v2-media");
        when(properties.publicBaseUrl()).thenReturn("https://simulator.example");
        SimulatorRepository repository = mock(SimulatorRepository.class);
        when(repository.currentRun()).thenReturn(Optional.of(new FixtureRun(
            "external-vehicle-provider-v2-media", "existing-reset-id", Instant.parse("2026-10-01T12:00:00Z"))));

        new SimulatorService(properties, repository, new ObjectMapper()).initializeFixture();

        verify(repository).currentRun();
        verifyNoMoreInteractions(repository);
    }
}
