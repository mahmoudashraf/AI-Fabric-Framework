package com.ai.behavior.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class BehaviorEventBatchRequest {

    @Valid
    @NotEmpty
    private List<BehaviorEventRequest> events = new ArrayList<>();
}
