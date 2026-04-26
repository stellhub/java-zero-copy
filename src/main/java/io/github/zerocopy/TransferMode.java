package io.github.zerocopy;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 传输模式枚举。
 */
public enum TransferMode {
    TRADITIONAL("traditional", "非零拷贝"),
    ZERO_COPY("zero-copy", "零拷贝"),
    MAPPED_BUFFER("mapped-buffer", "MappedByteBuffer");

    private final String cliValue;
    private final String displayName;

    TransferMode(String cliValue, String displayName) {
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
     * 解析命令行中的模式配置。
     */
    public static EnumSet<TransferMode> parseModes(String rawMode) {
        if (rawMode == null || rawMode.isBlank()) {
            return EnumSet.allOf(TransferMode.class);
        }
        String normalized = rawMode.toLowerCase(Locale.ROOT).trim();
        if ("all".equals(normalized)) {
            return EnumSet.allOf(TransferMode.class);
        }
        return Arrays.stream(normalized.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(TransferMode::parseSingleMode)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(TransferMode.class)));
    }

    private static TransferMode parseSingleMode(String rawMode) {
        return switch (rawMode) {
            case "traditional", "copy" -> TRADITIONAL;
            case "zero-copy", "zerocopy", "sendfile" -> ZERO_COPY;
            case "mapped-buffer", "mapped", "mmap" -> MAPPED_BUFFER;
            default -> throw new IllegalArgumentException("Unsupported mode: " + rawMode);
        };
    }
}
