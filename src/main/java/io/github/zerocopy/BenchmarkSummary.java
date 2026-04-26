package io.github.zerocopy;

import java.util.List;

/**
 * 同一模式下的聚合结果。
 */
public record BenchmarkSummary(
        ServerMode serverMode,
        TransferMode mode,
        double averageMs,
        double minMs,
        double maxMs,
        double averageServerReceiveMs,
        double averageMbPerSecond,
        double overallCpuUsagePercent,
        double averageSampledCpuUsagePercent,
        double peakSampledCpuUsagePercent,
        long bytesTransferred) {

    /**
     * 基于多次结果计算摘要。
     */
    public static BenchmarkSummary from(
            ServerMode serverMode, TransferMode mode, List<BenchmarkResult> results) {
        double averageMs =
                results.stream().mapToLong(BenchmarkResult::elapsedNanos).average().orElseThrow()
                        / 1_000_000.0D;
        double minMs =
                results.stream().mapToLong(BenchmarkResult::elapsedNanos).min().orElseThrow()
                        / 1_000_000.0D;
        double maxMs =
                results.stream().mapToLong(BenchmarkResult::elapsedNanos).max().orElseThrow()
                        / 1_000_000.0D;
        double averageServerReceiveMs =
                results.stream()
                        .mapToLong(BenchmarkResult::serverReceiveElapsedNanos)
                        .average()
                        .orElseThrow()
                        / 1_000_000.0D;
        double averageThroughput =
                results.stream()
                        .mapToDouble(BenchmarkResult::throughputMbPerSecond)
                        .average()
                        .orElseThrow();
        double overallCpuUsagePercent =
                results.stream()
                        .mapToDouble(BenchmarkResult::overallCpuUsagePercent)
                        .average()
                        .orElse(0.0D);
        double averageSampledCpuUsagePercent =
                results.stream()
                        .mapToDouble(BenchmarkResult::averageSampledCpuUsagePercent)
                        .average()
                        .orElse(0.0D);
        double peakSampledCpuUsagePercent =
                results.stream()
                        .mapToDouble(BenchmarkResult::peakSampledCpuUsagePercent)
                        .max()
                        .orElse(0.0D);
        long bytesTransferred = results.getFirst().bytesTransferred();
        return new BenchmarkSummary(
                serverMode,
                mode,
                averageMs,
                minMs,
                maxMs,
                averageServerReceiveMs,
                averageThroughput,
                overallCpuUsagePercent,
                averageSampledCpuUsagePercent,
                peakSampledCpuUsagePercent,
                bytesTransferred);
    }

    /**
     * 输出摘要信息。
     */
    public String toConsoleLine() {
        return "%s | %s summary: clientAvg=%.2f ms, min=%.2f ms, max=%.2f ms, serverAvg=%.2f ms, avgThroughput=%.2f MB/s, cpu(overall/avg/peak)=%.2f%%/%.2f%%/%.2f%%, bytes=%d"
                .formatted(
                        serverMode.displayName(),
                        mode.displayName(),
                        averageMs,
                        minMs,
                        maxMs,
                        averageServerReceiveMs,
                        averageMbPerSecond,
                        overallCpuUsagePercent,
                        averageSampledCpuUsagePercent,
                        peakSampledCpuUsagePercent,
                        bytesTransferred);
    }
}
