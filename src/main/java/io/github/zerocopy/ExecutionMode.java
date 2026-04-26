package io.github.zerocopy;

import java.util.Locale;

/**
 * 执行模式枚举。
 */
public enum ExecutionMode {
    LOCAL("local", "单进程内嵌服务端"),
    REMOTE("remote", "多进程独立服务端");

    private final String cliValue;
    private final String displayName;

    ExecutionMode(String cliValue, String displayName) {
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
     * 解析执行模式。
     */
    public static ExecutionMode parse(String rawValue) {
        String normalized = rawValue.toLowerCase(Locale.ROOT).trim();
        return switch (normalized) {
            case "local", "embedded" -> LOCAL;
            case "remote", "client-server" -> REMOTE;
            default -> throw new IllegalArgumentException("Unsupported execution-mode: " + rawValue);
        };
    }
}
