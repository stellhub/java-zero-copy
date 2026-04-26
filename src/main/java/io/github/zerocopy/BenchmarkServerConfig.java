package io.github.zerocopy;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * 独立服务端配置。
 */
public record BenchmarkServerConfig(
        String host,
        int port,
        int bufferSizeBytes,
        Path outputDirectory,
        boolean deleteReceivedFiles) {

    private static final int DEFAULT_BUFFER_SIZE_BYTES = 64 * 1024;
    private static final Path DEFAULT_OUTPUT_DIRECTORY = Paths.get("data", "server-received");

    /**
     * 解析服务端命令行参数。
     */
    public static BenchmarkServerConfig fromArgs(String[] args) {
        Map<String, String> options = CliSupport.parseOptions(args);
        String host = options.getOrDefault("host", "0.0.0.0");
        int port = Integer.parseInt(options.getOrDefault("port", "9090"));
        int bufferSizeBytes =
                (int)
                        CliSupport.parseSizeToBytes(
                                options.getOrDefault(
                                        "buffer-size", String.valueOf(DEFAULT_BUFFER_SIZE_BYTES)));
        Path outputDirectory =
                Paths.get(
                        options.getOrDefault(
                                "server-output-dir", DEFAULT_OUTPUT_DIRECTORY.toString()));
        boolean deleteReceivedFiles =
                Boolean.parseBoolean(options.getOrDefault("delete-received-files", "true"));
        validate(port, bufferSizeBytes);
        return new BenchmarkServerConfig(host, port, bufferSizeBytes, outputDirectory, deleteReceivedFiles);
    }

    /**
     * 返回输出目录绝对路径。
     */
    public Path normalizedOutputDirectory() {
        return outputDirectory.toAbsolutePath().normalize();
    }

    /**
     * 输出简要配置。
     */
    public String describe() {
        return """
                host=%s
                port=%d
                bufferSize=%s
                serverOutputDir=%s
                deleteReceivedFiles=%s
                """
                .formatted(
                        host,
                        port,
                        CliSupport.formatBytes(bufferSizeBytes),
                        normalizedOutputDirectory(),
                        deleteReceivedFiles);
    }

    private static void validate(int port, int bufferSizeBytes) {
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("port must be between 0 and 65535");
        }
        if (bufferSizeBytes <= 0) {
            throw new IllegalArgumentException("buffer-size must be greater than 0");
        }
    }
}
