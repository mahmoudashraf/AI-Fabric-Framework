package com.loomai.operations.lifecycle;

import com.loomai.operations.lifecycle.LifecycleContracts.Command;
import com.loomai.operations.lifecycle.LifecycleContracts.Receipt;

public interface LifecycleGateway {
    default boolean configured() { return true; }
    Receipt submit(Command command);
    Receipt observe(Command command);
}
