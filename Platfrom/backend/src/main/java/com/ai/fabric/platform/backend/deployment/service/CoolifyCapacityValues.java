package com.ai.fabric.platform.backend.deployment.service;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class CoolifyCapacityValues {
    private static final Pattern MEMORY = Pattern.compile("^(\\d+)([kmgt]i?b?|b)?$", Pattern.CASE_INSENSITIVE);

    private CoolifyCapacityValues() { }

    static long memoryBytes(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Missing memory limit.");
        }
        Matcher matcher = MEMORY.matcher(value.trim().toLowerCase(Locale.ROOT));
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid memory limit.");
        }
        long amount = Long.parseLong(matcher.group(1));
        String unit = matcher.group(2);
        long multiplier = switch (unit == null ? "b" : unit) {
            case "k", "kb", "kib" -> 1024L;
            case "m", "mb", "mib" -> 1024L * 1024L;
            case "g", "gb", "gib" -> 1024L * 1024L * 1024L;
            case "t", "tb", "tib" -> 1024L * 1024L * 1024L * 1024L;
            case "b" -> 1L;
            default -> throw new IllegalArgumentException("Invalid memory unit.");
        };
        return Math.multiplyExact(amount, multiplier);
    }
}
