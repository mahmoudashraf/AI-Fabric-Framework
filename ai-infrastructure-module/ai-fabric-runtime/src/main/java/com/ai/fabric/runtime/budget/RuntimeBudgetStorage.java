package com.ai.fabric.runtime.budget;

import java.util.Objects;
import javax.sql.DataSource;

/** Not a DataSource bean: must not replace/back off the application's primary DataSource auto-configuration. */
public record RuntimeBudgetStorage(DataSource independentDataSource) {
    public RuntimeBudgetStorage { Objects.requireNonNull(independentDataSource); }
}
