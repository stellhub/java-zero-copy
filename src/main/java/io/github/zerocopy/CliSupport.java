package io.github.zerocopy;

import java.util.HashMap;
import java.util.Map;

/**
 * 命令行工具方法。
 */
public final class CliSupport {

    private CliSupport() {}

    /**
     * 解析命令行参数。
     */
    public static Map<String, String> parseOptions(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (String arg : args) {
            if (!arg.startsWith("--") || !arg.contains("=")) {
                throw new IllegalArgumentException("Invalid argument: " + arg);
            }
            int separator = arg.indexOf('=');
            String key = arg.substring(2, separator).trim();
            String value = arg.substring(separator + 1).trim();
            options.put(key, value);
        }
        return options;
    }

    /**
     * 将字符串大小解析为字节数。
     */
    public static long parseSizeToBytes(String rawValue) {
        String normalized = rawValue.trim().toUpperCase();
        if (normalized.endsWith("KB")) {
            return Long.parseLong(normalized.substring(0, normalized.length() - 2).trim()) * 1024L;
        }
        if (normalized.endsWith("MB")) {
            return Long.parseLong(normalized.substring(0, normalized.length() - 2).trim())
                    * 1024L
                    * 1024L;
        }
        if (normalized.endsWith("GB")) {
            return Long.parseLong(normalized.substring(0, normalized.length() - 2).trim())
                    * 1024L
                    * 1024L
                    * 1024L;
        }
        if (normalized.endsWith("B")) {
            return Long.parseLong(normalized.substring(0, normalized.length() - 1).trim());
        }
        return Long.parseLong(normalized);
    }

    /**
     * 将字节数格式化为便于阅读的文本。
     */
    public static String formatBytes(long bytes) {
        double value = bytes;
        String[] units = {"B", "KB", "MB", "GB"};
        int index = 0;
        while (value >= 1024 && index < units.length - 1) {
            value /= 1024;
            index++;
        }
        return "%.2f %s".formatted(value, units[index]);
    }

    /**
     * 将任意字符串清洗为文件名安全片段。
     */
    public static String sanitizeToken(String rawValue) {
        return rawValue.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
