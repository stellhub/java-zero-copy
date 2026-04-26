package io.github.zerocopy;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 服务端接收模式枚举。
 */
public enum ServerMode {
    MEMORY("memory", "服务端只接收"),
    DISK("disk", "服务端接收并落盘");

    private final String cliValue;
    private final String displayName;

    ServerMode(String cliValue, String displayName) {
        this.cliValue = cliValue;
        this.displayName = displayName;
    }

    public String cliValue() {
        return cliValue;
    }

    public String displayName() {
        return displayName;
    }

    /**
     * 解析命令行中的服务端模式配置。
     */
    public static EnumSet<ServerMode> parseModes(String rawMode) {
        if (rawMode == null || rawMode.isBlank()) {
            return EnumSet.allOf(ServerMode.class);
        }
        String normalized = rawMode.toLowerCase(Locale.ROOT).trim();
        if ("all".equals(normalized)) {
            return EnumSet.allOf(ServerMode.class);
        }
        return Arrays.stream(normalized.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(ServerMode::parseSingleMode)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(ServerMode.class)));
    }

    private static ServerMode parseSingleMode(String rawMode) {
        return switch (rawMode) {
            case "memory", "receive-only" -> MEMORY;
            case "disk", "flush", "persist" -> DISK;
            default -> throw new IllegalArgumentException("Unsupported server-mode: " + rawMode);
        };
    }
}
