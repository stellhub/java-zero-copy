package io.github.zerocopy;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 网络零拷贝与非零拷贝对比入口。
 */
public final class ZeroCopyBenchmarkApplication {

    private ZeroCopyBenchmarkApplication() {}

    /**
     * 启动 benchmark。
     */
    public static void main(String[] args) throws Exception {
        BenchmarkConfig config = BenchmarkConfig.fromArgs(args);
        PreparedFile preparedFile = BenchmarkFilePreparer.prepare(config);
        System.out.println("=== Java 网络传输零拷贝基准测试 ===");
        System.out.println(config.describe());
        System.out.println(
                "preparedFile=%s, generated=%s"
                        .formatted(preparedFile.path(), preparedFile.generated()));

        TransferBenchmarkRunner runner = new TransferBenchmarkRunner();
        try {
            BenchmarkExecutionReport report = runner.run(config, preparedFile);
            printSummary(report.summaries());
            List<Path> outputFiles = new BenchmarkReportWriter().write(report);
            outputFiles.forEach(path -> System.out.println("reportFile=" + path));
        } finally {
            BenchmarkFilePreparer.deleteIfGenerated(config, preparedFile);
        }
    }

    /**
     * 输出最终结果。
     */
    public static void printSummary(List<BenchmarkSummary> summaries) {
        System.out.println("=== Summary ===");
        Map<ServerMode, List<BenchmarkSummary>> groupedSummaries =
                summaries.stream().collect(Collectors.groupingBy(BenchmarkSummary::serverMode));
        for (Map.Entry<ServerMode, List<BenchmarkSummary>> entry : groupedSummaries.entrySet()) {
            System.out.println("scenario=" + entry.getKey().displayName());
            entry.getValue().stream()
                    .sorted(Comparator.comparing(summary -> summary.mode().ordinal()))
                    .forEach(summary -> System.out.println(summary.toConsoleLine()));
            Map<TransferMode, BenchmarkSummary> summaryMap =
                    entry.getValue().stream()
                            .collect(Collectors.toMap(BenchmarkSummary::mode, summary -> summary));
            printComparison(summaryMap, TransferMode.ZERO_COPY);
            printComparison(summaryMap, TransferMode.MAPPED_BUFFER);
        }
        System.out.println(
                "说明: local 模式下 CPU 为单 JVM 聚合值；remote 模式下这里展示的是客户端 CPU，服务端建议结合系统工具或落盘耗时一起观察。");
    }

    private static void printComparison(
            Map<TransferMode, BenchmarkSummary> summaries, TransferMode candidateMode) {
        Optional.ofNullable(summaries.get(TransferMode.TRADITIONAL))
                .flatMap(
                        traditional ->
                                Optional.ofNullable(summaries.get(candidateMode))
                                        .map(candidate -> formatComparison(traditional, candidate)))
                .ifPresent(System.out::println);
    }

    private static String formatComparison(
            BenchmarkSummary traditional, BenchmarkSummary candidate) {
        double elapsedRatio = traditional.averageMs() / candidate.averageMs();
        double throughputGain =
                (candidate.averageMbPerSecond() - traditional.averageMbPerSecond())
                        / traditional.averageMbPerSecond()
                        * 100.0D;
        double cpuDelta = candidate.overallCpuUsagePercent() - traditional.overallCpuUsagePercent();
        double serverTimeDelta =
                candidate.averageServerReceiveMs() - traditional.averageServerReceiveMs();
        if (elapsedRatio >= 1.0D) {
            return "comparison: %s elapsed improvement=%.2fx, throughput gain=%.2f%%, cpu delta=%.2f%%, serverTime delta=%.2f ms"
                    .formatted(
                            candidate.mode().displayName(),
                            elapsedRatio,
                            throughputGain,
                            cpuDelta,
                            serverTimeDelta);
        }
        return "comparison: %s elapsed is %.2fx of traditional, throughput gain=%.2f%%, cpu delta=%.2f%%, serverTime delta=%.2f ms"
                .formatted(
                        candidate.mode().displayName(),
                        1.0D / elapsedRatio,
                        throughputGain,
                        cpuDelta,
                        serverTimeDelta);
    }
}
