package io.github.zerocopy;

/**
 * 单次传输结果。
 */
public record BenchmarkResult(
        ServerMode serverMode,
        TransferMode mode,
        int iteration,
        long bytesTransferred,
        long elapsedNanos,
        long serverReceiveElapsedNanos,
        double overallCpuUsagePercent,
        double averageSampledCpuUsagePercent,
        double peakSampledCpuUsagePercent,
        int cpuSampleCount) {

    /**
     * 计算 MB/s 吞吐量。
     */
    public double throughputMbPerSecond() {
        double elapsedSeconds = elapsedNanos / 1_000_000_000.0D;
        double sizeInMb = bytesTransferred / 1024.0D / 1024.0D;
        return sizeInMb / elapsedSeconds;
    }

    /**
     * 计算客户端总耗时毫秒数。
     */
    public double elapsedMs() {
        return elapsedNanos / 1_000_000.0D;
    }

    /**
     * 计算服务端接收耗时毫秒数。
     */
    public double serverReceiveMs() {
        return serverReceiveElapsedNanos / 1_000_000.0D;
    }

    /**
     * 生成控制台展示文本。
     */
    public String toConsoleLine() {
        return "%s | %s run-%d: clientTime=%.2f ms, serverTime=%.2f ms, throughput=%.2f MB/s, cpu(overall/avg/peak)=%.2f%%/%.2f%%/%.2f%%, samples=%d, bytes=%d"
                .formatted(
                        serverMode.displayName(),
                        mode.displayName(),
                        iteration,
                        elapsedMs(),
                        serverReceiveMs(),
                        throughputMbPerSecond(),
                        overallCpuUsagePercent,
                        averageSampledCpuUsagePercent,
                        peakSampledCpuUsagePercent,
                        cpuSampleCount,
                        bytesTransferred);
    }
}
