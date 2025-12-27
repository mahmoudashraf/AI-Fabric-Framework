package com.ai.infrastructure.behavior.api;

import com.ai.infrastructure.behavior.config.BehaviorProcessingProperties;
import com.ai.infrastructure.behavior.entity.BehaviorInsights;
import com.ai.infrastructure.behavior.service.BehaviorAnalysisService;
import com.ai.infrastructure.behavior.state.BehaviorProcessingState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class BehaviorProcessingControllerTest {

    @Mock
    private BehaviorAnalysisService analysisService;

    private BehaviorProcessingProperties properties;
    private BehaviorProcessingState state;
    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        properties = new BehaviorProcessingProperties();
        state = new BehaviorProcessingState();
        BehaviorProcessingController controller = new BehaviorProcessingController(analysisService, properties, state);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void analyzeUserReturnsOk() throws Exception {
        BehaviorInsights insight = BehaviorInsights.builder()
            .userId(UUID.randomUUID())
            .segment("seg")
            .build();
        Mockito.when(analysisService.analyzeUser(any())).thenReturn(insight);

        mockMvc.perform(post("/api/behavior/processing/users/{id}", UUID.randomUUID()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.segment").value("seg"));
    }

    @Test
    void batchHonorsMaxUsers() throws Exception {
        properties.setApiMaxBatchSize(1);
        Mockito.when(analysisService.processNextUser())
            .thenReturn(BehaviorInsights.builder().userId(UUID.randomUUID()).build())
            .thenReturn(null);

        mockMvc.perform(post("/api/behavior/processing/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"maxUsers\":5,\"maxDurationMinutes\":1}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.processedCount").value(1));
    }
}
