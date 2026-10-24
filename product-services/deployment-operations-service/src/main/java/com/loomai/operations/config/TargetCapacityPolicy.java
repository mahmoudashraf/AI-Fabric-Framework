package com.loomai.operations.config;

import com.loomai.operations.model.OperationsContracts;
import com.loomai.operations.model.OperationsContracts.ResourceDemand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record TargetCapacityPolicy(
    @NotNull @Pattern(regexp = OperationsContracts.ID) String revision,
    @NotNull @Size(max = 256) Map<@Pattern(regexp = OperationsContracts.ID) String, @NotNull @Valid ResourceDemand> targets
) {
    public TargetCapacityPolicy {
        if (targets != null) {
            targets = Map.copyOf(targets);
        }
    }
}
