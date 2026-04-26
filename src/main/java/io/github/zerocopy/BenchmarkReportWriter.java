package io.github.zerocopy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Benchmark 报告导出器。
 */
public class BenchmarkReportWriter {

    private final ObjectMapper objectMapper;

    public BenchmarkReportWriter() {
        this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * 将报告写入磁盘。
     */
    public List<Path> write(BenchmarkExecutionReport report) throws IOException {
        List<Path> outputFiles = new ArrayList<>();
        Files.createDirectories(report.config().normalizedResultDirectory());
        if (report.config().writeCsv()) {
            outputFiles.add(writeResultsCsv(report));
            outputFiles.add(writeSummaryCsv(report));
        }
        if (report.config().writeJson()) {
            outputFiles.add(writeJson(report));
        }
        return outputFiles;
    }

    private Path writeResultsCsv(BenchmarkExecutionReport report) throws IOException {
        Path targetPath =
                report.config()
                        .normalizedResultDirectory()
                        .resolve(report.config().resultPrefix() + "-results.csv");
        try (Writer writer = Files.newBufferedWriter(targetPath)) {
            writer.write(
                    "executionMode,serverMode,transferMode,iteration,bytesTransferred,elapsedMs,serverReceiveMs,throughputMbPerSecond,cpuOverallPercent,cpuAveragePercent,cpuPeakPercent,cpuSampleCount\n");
            for (BenchmarkResult result : report.results()) {
                writer.write(
                        "%s,%s,%s,%d,%d,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%d\n"
                                .formatted(
                                        report.config().executionMode().cliValue(),
                                        result.serverMode().cliValue(),
                                        result.mode().cliValue(),
                                        result.iteration(),
                                        result.bytesTransferred(),
                                        result.elapsedMs(),
                                        result.serverReceiveMs(),
                                        result.throughputMbPerSecond(),
                                        result.overallCpuUsagePercent(),
                                        result.averageSampledCpuUsagePercent(),
                                        result.peakSampledCpuUsagePercent(),
                                        result.cpuSampleCount()));
            }
        }
        return targetPath;
    }

    private Path writeSummaryCsv(BenchmarkExecutionReport report) throws IOException {
        Path targetPath =
                report.config()
                        .normalizedResultDirectory()
                        .resolve(report.config().resultPrefix() + "-summary.csv");
        try (Writer writer = Files.newBufferedWriter(targetPath)) {
            writer.write(
                    "executionMode,serverMode,transferMode,avgMs,minMs,maxMs,avgServerReceiveMs,avgThroughputMbPerSecond,cpuOverallPercent,cpuAveragePercent,cpuPeakPercent,bytesTransferred\n");
            for (BenchmarkSummary summary : report.summaries()) {
                writer.write(
                        "%s,%s,%s,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%d\n"
                                .formatted(
                                        report.config().executionMode().cliValue(),
                                        summary.serverMode().cliValue(),
                                        summary.mode().cliValue(),
                                        summary.averageMs(),
                                        summary.minMs(),
                                        summary.maxMs(),
                                        summary.averageServerReceiveMs(),
                                        summary.averageMbPerSecond(),
                                        summary.overallCpuUsagePercent(),
                                        summary.averageSampledCpuUsagePercent(),
                                        summary.peakSampledCpuUsagePercent(),
                                        summary.bytesTransferred()));
            }
        }
        return targetPath;
    }

    private Path writeJson(BenchmarkExecutionReport report) throws IOException {
        Path targetPath =
                report.config()
                        .normalizedResultDirectory()
                        .resolve(report.config().resultPrefix() + "-report.json");
        objectMapper.writeValue(targetPath.toFile(), report);
        return targetPath;
    }
}
