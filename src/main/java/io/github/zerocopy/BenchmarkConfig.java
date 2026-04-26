package io.github.zerocopy;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.Map;

/**
 * Benchmark 运行配置。
 */
public record BenchmarkConfig(
        ExecutionMode executionMode,
        Path filePath,
        long fileSizeBytes,
        int bufferSizeBytes,
        int mappedChunkSizeBytes,
        int warmupIterations,
        int measureIterations,
        int cpuSampleIntervalMs,
        String host,
        int port,
        Path serverOutputDirectory,
        boolean deleteGeneratedFile,
        boolean deleteReceivedFiles,
        Path resultDirectory,
        String resultPrefix,
        boolean writeCsv,
        boolean writeJson,
        EnumSet<TransferMode> modes,
        EnumSet<ServerMode> serverModes) {

    private static final Path DEFAULT_FILE_PATH = Paths.get("data", "zero-copy-benchmark.bin");
    private static final long DEFAULT_FILE_SIZE_BYTES = 256L * 1024L * 1024L;
    private static final int DEFAULT_BUFFER_SIZE_BYTES = 64 * 1024;
    private static final int DEFAULT_MAPPED_CHUNK_SIZE_BYTES = 32 * 1024 * 1024;
    private static final int DEFAULT_CPU_SAMPLE_INTERVAL_MS = 50;
    private static final Path DEFAULT_SERVER_OUTPUT_PATH = Paths.get("data", "received");
    private static final Path DEFAULT_RESULT_DIRECTORY = Paths.get("data", "results");

    /**
     * 解析命令行参数。
     */
    public static BenchmarkConfig fromArgs(String[] args) {
        Map<String, String> options = CliSupport.parseOptions(args);
        ExecutionMode executionMode =
                ExecutionMode.parse(options.getOrDefault("execution-mode", "local"));
        Path filePath = Paths.get(options.getOrDefault("file", DEFAULT_FILE_PATH.toString()));
        long fileSizeBytes =
                CliSupport.parseSizeToBytes(
                        options.getOrDefault("file-size", String.valueOf(DEFAULT_FILE_SIZE_BYTES)));
        int bufferSizeBytes =
                (int)
                        CliSupport.parseSizeToBytes(
                                options.getOrDefault(
                                        "buffer-size", String.valueOf(DEFAULT_BUFFER_SIZE_BYTES)));
        int mappedChunkSizeBytes =
                (int)
                        CliSupport.parseSizeToBytes(
                                options.getOrDefault(
                                        "mapped-chunk-size",
                                        String.valueOf(DEFAULT_MAPPED_CHUNK_SIZE_BYTES)));
        int warmupIterations = Integer.parseInt(options.getOrDefault("warmup", "1"));
        int measureIterations = Integer.parseInt(options.getOrDefault("iterations", "3"));
        int cpuSampleIntervalMs =
                Integer.parseInt(
                        options.getOrDefault(
                                "cpu-sample-interval-ms",
                                String.valueOf(DEFAULT_CPU_SAMPLE_INTERVAL_MS)));
        String host = options.getOrDefault("host", "127.0.0.1");
        int port = Integer.parseInt(options.getOrDefault("port", "0"));
        Path serverOutputDirectory =
                Paths.get(
                        options.getOrDefault(
                                "server-output-dir", DEFAULT_SERVER_OUTPUT_PATH.toString()));
        boolean deleteGeneratedFile =
                Boolean.parseBoolean(options.getOrDefault("delete-generated-file", "false"));
        boolean deleteReceivedFiles =
                Boolean.parseBoolean(options.getOrDefault("delete-received-files", "true"));
        Path resultDirectory =
                Paths.get(
                        options.getOrDefault(
                                "result-dir", DEFAULT_RESULT_DIRECTORY.toString()));
        String resultPrefix = options.getOrDefault("result-prefix", "benchmark");
        boolean writeCsv = Boolean.parseBoolean(options.getOrDefault("write-csv", "true"));
        boolean writeJson = Boolean.parseBoolean(options.getOrDefault("write-json", "true"));
        EnumSet<TransferMode> modes =
                TransferMode.parseModes(options.getOrDefault("mode", "all"));
        EnumSet<ServerMode> serverModes =
                ServerMode.parseModes(options.getOrDefault("server-mode", "all"));
        validate(
                executionMode,
                bufferSizeBytes,
                mappedChunkSizeBytes,
                warmupIterations,
                measureIterations,
                cpuSampleIntervalMs,
                fileSizeBytes,
                port,
                resultPrefix,
                writeCsv,
                writeJson);
        return new BenchmarkConfig(
                executionMode,
                filePath,
                fileSizeBytes,
                bufferSizeBytes,
                mappedChunkSizeBytes,
                warmupIterations,
                measureIterations,
                cpuSampleIntervalMs,
                host,
                port,
                serverOutputDirectory,
                deleteGeneratedFile,
                deleteReceivedFiles,
                resultDirectory,
                resultPrefix,
                writeCsv,
                writeJson,
                modes,
                serverModes);
    }

    /**
     * 输出简要配置说明。
     */
    public String describe() {
        return """
                executionMode=%s
                file=%s
                fileSize=%s
                bufferSize=%s
                mappedChunkSize=%s
                warmup=%d
                iterations=%d
                cpuSampleIntervalMs=%d
                host=%s
                port=%d
                mode=%s
                serverMode=%s
                serverOutputDir=%s
                deleteGeneratedFile=%s
                deleteReceivedFiles=%s
                resultDir=%s
                resultPrefix=%s
                writeCsv=%s
                writeJson=%s
                """
                .formatted(
                        executionMode.displayName(),
                        filePath,
                        CliSupport.formatBytes(fileSizeBytes),
                        CliSupport.formatBytes(bufferSizeBytes),
                        CliSupport.formatBytes(mappedChunkSizeBytes),
                        warmupIterations,
                        measureIterations,
                        cpuSampleIntervalMs,
                        host,
                        port,
                        modes,
                        serverModes,
                        normalizedServerOutputDirectory(),
                        deleteGeneratedFile,
                        deleteReceivedFiles,
                        normalizedResultDirectory(),
                        resultPrefix,
                        writeCsv,
                        writeJson);
    }

    /**
     * 返回落盘目录的绝对路径。
     */
    public Path normalizedServerOutputDirectory() {
        return serverOutputDirectory.toAbsolutePath().normalize();
    }

    /**
     * 返回结果目录的绝对路径。
     */
    public Path normalizedResultDirectory() {
        return resultDirectory.toAbsolutePath().normalize();
    }

    private static void validate(
            ExecutionMode executionMode,
            int bufferSizeBytes,
            int mappedChunkSizeBytes,
            int warmupIterations,
            int measureIterations,
            int cpuSampleIntervalMs,
            long fileSizeBytes,
            int port,
            String resultPrefix,
            boolean writeCsv,
            boolean writeJson) {
        if (bufferSizeBytes <= 0) {
            throw new IllegalArgumentException("buffer-size must be greater than 0");
        }
        if (mappedChunkSizeBytes <= 0) {
            throw new IllegalArgumentException("mapped-chunk-size must be greater than 0");
        }
        if (warmupIterations < 0) {
            throw new IllegalArgumentException("warmup must be greater than or equal to 0");
        }
        if (measureIterations <= 0) {
            throw new IllegalArgumentException("iterations must be greater than 0");
        }
        if (cpuSampleIntervalMs <= 0) {
            throw new IllegalArgumentException("cpu-sample-interval-ms must be greater than 0");
        }
        if (fileSizeBytes <= 0) {
            throw new IllegalArgumentException("file-size must be greater than 0");
        }
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("port must be between 0 and 65535");
        }
        if (executionMode == ExecutionMode.REMOTE && port == 0) {
            throw new IllegalArgumentException("remote mode requires an explicit port");
        }
        if (resultPrefix == null || resultPrefix.isBlank()) {
            throw new IllegalArgumentException("result-prefix must not be blank");
        }
        if (!writeCsv && !writeJson) {
            throw new IllegalArgumentException("write-csv and write-json cannot both be false");
        }
    }
}
