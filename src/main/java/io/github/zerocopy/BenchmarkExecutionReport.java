package io.github.zerocopy;

import java.util.List;

/**
 * Benchmark 执行报告。
 */
public record BenchmarkExecutionReport(
        String generatedAt,
        BenchmarkConfig config,
        List<BenchmarkResult> results,
        List<BenchmarkSummary> summaries) {}
