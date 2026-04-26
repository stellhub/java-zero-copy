package io.github.zerocopy;

/**
 * CPU 采样结果。
 */
public record CpuUsageSnapshot(
        double overallCpuUsagePercent,
        double averageSampledCpuUsagePercent,
        double peakSampledCpuUsagePercent,
        int sampleCount) {}
