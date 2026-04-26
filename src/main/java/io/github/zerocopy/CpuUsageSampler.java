package io.github.zerocopy;

import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 采样 JVM 进程 CPU 使用率。
 */
public final class CpuUsageSampler implements AutoCloseable {

    private final OperatingSystemMXBean operatingSystemMXBean;
    private final int availableProcessors;
    private final int sampleIntervalMs;
    private final ScheduledExecutorService executorService;
    private final List<Double> samples;

    private volatile long startWallTimeNanos;
    private volatile long startCpuTimeNanos;
    private volatile long lastWallTimeNanos;
    private volatile long lastCpuTimeNanos;

    public CpuUsageSampler(int sampleIntervalMs) {
        this.operatingSystemMXBean =
                ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
        this.availableProcessors = Math.max(1, Runtime.getRuntime().availableProcessors());
        this.sampleIntervalMs = sampleIntervalMs;
        this.executorService =
                Executors.newSingleThreadScheduledExecutor(
                        runnable -> {
                            Thread thread = new Thread(runnable, "cpu-usage-sampler");
                            thread.setDaemon(true);
                            return thread;
                        });
        this.samples = Collections.synchronizedList(new ArrayList<>());
    }

    /**
     * 启动采样。
     */
    public void start() {
        startWallTimeNanos = System.nanoTime();
        startCpuTimeNanos = readProcessCpuTime();
        lastWallTimeNanos = startWallTimeNanos;
        lastCpuTimeNanos = startCpuTimeNanos;
        executorService.scheduleAtFixedRate(
                this::sample, sampleIntervalMs, sampleIntervalMs, TimeUnit.MILLISECONDS);
    }

    /**
     * 停止采样并返回统计结果。
     */
    public CpuUsageSnapshot stop() {
        executorService.shutdownNow();
        long endWallTimeNanos = System.nanoTime();
        long endCpuTimeNanos = readProcessCpuTime();
        addSample(lastCpuTimeNanos, lastWallTimeNanos, endCpuTimeNanos, endWallTimeNanos);

        double overallCpuUsagePercent =
                calculateCpuUsagePercent(
                        endCpuTimeNanos - startCpuTimeNanos, endWallTimeNanos - startWallTimeNanos);
        List<Double> sampleSnapshot;
        synchronized (samples) {
            if (samples.isEmpty()) {
                samples.add(overallCpuUsagePercent);
            }
            sampleSnapshot = new ArrayList<>(samples);
        }

        double averageSampledCpuUsagePercent =
                sampleSnapshot.stream().mapToDouble(Double::doubleValue).average().orElse(0.0D);
        double peakSampledCpuUsagePercent =
                sampleSnapshot.stream().mapToDouble(Double::doubleValue).max().orElse(0.0D);
        return new CpuUsageSnapshot(
                overallCpuUsagePercent,
                averageSampledCpuUsagePercent,
                peakSampledCpuUsagePercent,
                sampleSnapshot.size());
    }

    @Override
    public void close() {
        executorService.shutdownNow();
    }

    private void sample() {
        long currentWallTimeNanos = System.nanoTime();
        long currentCpuTimeNanos = readProcessCpuTime();
        addSample(lastCpuTimeNanos, lastWallTimeNanos, currentCpuTimeNanos, currentWallTimeNanos);
        lastWallTimeNanos = currentWallTimeNanos;
        lastCpuTimeNanos = currentCpuTimeNanos;
    }

    private void addSample(
            long startCpuTimeNanos,
            long startWallTimeNanos,
            long endCpuTimeNanos,
            long endWallTimeNanos) {
        if (endWallTimeNanos <= startWallTimeNanos) {
            return;
        }
        double cpuUsagePercent =
                calculateCpuUsagePercent(
                        endCpuTimeNanos - startCpuTimeNanos, endWallTimeNanos - startWallTimeNanos);
        samples.add(cpuUsagePercent);
    }

    private double calculateCpuUsagePercent(long cpuDeltaNanos, long wallDeltaNanos) {
        if (cpuDeltaNanos < 0 || wallDeltaNanos <= 0) {
            return 0.0D;
        }
        return cpuDeltaNanos * 100.0D / wallDeltaNanos / availableProcessors;
    }

    private long readProcessCpuTime() {
        if (operatingSystemMXBean == null) {
            return 0L;
        }
        long cpuTime = operatingSystemMXBean.getProcessCpuTime();
        return Math.max(cpuTime, 0L);
    }
}
