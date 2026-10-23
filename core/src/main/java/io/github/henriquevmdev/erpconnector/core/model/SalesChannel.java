package io.github.henriquevmdev.erpconnector.core.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Where the order came from. If the ERP does not say, the type is {@link Type#UNKNOWN}, never {@link Type#DIRECT}
 * (AGENTS.md, hard rule 5).
 */
public record SalesChannel(Type type, Optional<String> raw) {

    public enum Type { DIRECT, MARKETPLACE, UNKNOWN }

    public SalesChannel {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(raw, "raw");
    }
}
