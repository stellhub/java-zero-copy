package io.github.zerocopy;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 执行网络传输对比测试。
 */
public class TransferBenchmarkRunner {

    /**
     * 运行所有配置好的 benchmark。
     */
    public BenchmarkExecutionReport run(BenchmarkConfig config, PreparedFile preparedFile)
            throws IOException, InterruptedException {
        List<BenchmarkResult> allResults = new ArrayList<>();
        List<BenchmarkSummary> summaries = new ArrayList<>();
        try (LocalServerContext localServerContext = createLocalServerContextIfNeeded(config)) {
            String host = resolveHost(config);
            int port = resolvePort(config, localServerContext);
            for (ServerMode serverMode : config.serverModes()) {
                for (TransferMode mode : config.modes()) {
                    runWarmup(config, preparedFile, host, port, serverMode, mode);
                    List<BenchmarkResult> results =
                            runMeasure(config, preparedFile, host, port, serverMode, mode);
                    allResults.addAll(results);
                    summaries.add(BenchmarkSummary.from(serverMode, mode, results));
                }
            }
        }
        return new BenchmarkExecutionReport(
                OffsetDateTime.now().toString(), config, List.copyOf(allResults), List.copyOf(summaries));
    }

    /**
     * 运行预热轮次。
     */
    public void runWarmup(
            BenchmarkConfig config,
            PreparedFile preparedFile,
            String host,
            int port,
            ServerMode serverMode,
            TransferMode mode)
            throws IOException {
        for (int index = 1; index <= config.warmupIterations(); index++) {
            BenchmarkResult result =
                    runSingle(config, preparedFile, host, port, serverMode, mode, index, "warmup");
            System.out.println("[warmup] " + result.toConsoleLine());
        }
    }

    /**
     * 运行正式测量轮次。
     */
    public List<BenchmarkResult> runMeasure(
            BenchmarkConfig config,
            PreparedFile preparedFile,
            String host,
            int port,
            ServerMode serverMode,
            TransferMode mode)
            throws IOException {
        List<BenchmarkResult> results = new ArrayList<>();
        for (int index = 1; index <= config.measureIterations(); index++) {
            BenchmarkResult result =
                    runSingle(config, preparedFile, host, port, serverMode, mode, index, "measure");
            results.add(result);
            System.out.println("[measure] " + result.toConsoleLine());
        }
        return results;
    }

    private BenchmarkResult runSingle(
            BenchmarkConfig config,
            PreparedFile preparedFile,
            String host,
            int port,
            ServerMode serverMode,
            TransferMode mode,
            int iteration,
            String stage)
            throws IOException {
        String runId =
                "%s-%s-%s-%d-%d"
                        .formatted(
                                config.executionMode().cliValue(),
                                mode.cliValue(),
                                stage,
                                iteration,
                                System.nanoTime());
        try (CpuUsageSampler cpuUsageSampler = new CpuUsageSampler(config.cpuSampleIntervalMs())) {
            cpuUsageSampler.start();
            long startNanos = System.nanoTime();
            TransferResponse response =
                    sendFile(mode, preparedFile, host, port, config, serverMode, runId);
            long elapsedNanos = System.nanoTime() - startNanos;
            CpuUsageSnapshot cpuUsageSnapshot = cpuUsageSampler.stop();
            if (response.receivedBytes() != preparedFile.sizeBytes()) {
                throw new IllegalStateException(
                        "Expected received bytes "
                                + preparedFile.sizeBytes()
                                + " but got "
                                + response.receivedBytes());
            }
            return new BenchmarkResult(
                    serverMode,
                    mode,
                    iteration,
                    response.receivedBytes(),
                    elapsedNanos,
                    response.serverElapsedNanos(),
                    cpuUsageSnapshot.overallCpuUsagePercent(),
                    cpuUsageSnapshot.averageSampledCpuUsagePercent(),
                    cpuUsageSnapshot.peakSampledCpuUsagePercent(),
                    cpuUsageSnapshot.sampleCount());
        }
    }

    private TransferResponse sendFile(
            TransferMode mode,
            PreparedFile preparedFile,
            String host,
            int port,
            BenchmarkConfig config,
            ServerMode serverMode,
            String runId)
            throws IOException {
        try (SocketChannel socketChannel = SocketChannel.open()) {
            socketChannel.setOption(StandardSocketOptions.TCP_NODELAY, true);
            socketChannel.connect(new InetSocketAddress(host, port));
            TransferProtocol.writeRequestHeader(
                    socketChannel.socket().getOutputStream(),
                    new TransferRequestHeader(serverMode, mode, runId, preparedFile.sizeBytes()));
            switch (mode) {
                case TRADITIONAL ->
                        sendWithStreamCopy(
                                preparedFile,
                                socketChannel,
                                config.bufferSizeBytes());
                case ZERO_COPY -> sendWithTransferTo(preparedFile, socketChannel);
                case MAPPED_BUFFER ->
                        sendWithMappedBuffer(
                                preparedFile,
                                socketChannel,
                                config.mappedChunkSizeBytes());
            }
            socketChannel.shutdownOutput();
            return TransferProtocol.readResponse(socketChannel.socket().getInputStream());
        }
    }

    private void sendWithStreamCopy(
            PreparedFile preparedFile, SocketChannel socketChannel, int bufferSizeBytes)
            throws IOException {
        byte[] buffer = new byte[bufferSizeBytes];
        try (BufferedInputStream inputStream =
                new BufferedInputStream(
                        Files.newInputStream(preparedFile.path()), bufferSizeBytes)) {
            BufferedOutputStream outputStream =
                    new BufferedOutputStream(
                            socketChannel.socket().getOutputStream(), bufferSizeBytes);
            int readBytes;
            while ((readBytes = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, readBytes);
            }
            outputStream.flush();
        }
    }

    private void sendWithTransferTo(PreparedFile preparedFile, SocketChannel socketChannel)
            throws IOException {
        try (FileChannel fileChannel = FileChannel.open(preparedFile.path())) {
            long position = 0L;
            int zeroProgressCount = 0;
            while (position < preparedFile.sizeBytes()) {
                long transferred =
                        fileChannel.transferTo(
                                position, preparedFile.sizeBytes() - position, socketChannel);
                if (transferred <= 0) {
                    zeroProgressCount++;
                    if (zeroProgressCount > 16) {
                        throw new IOException("transferTo made no progress for 16 consecutive attempts");
                    }
                    Thread.yield();
                    continue;
                }
                zeroProgressCount = 0;
                position += transferred;
            }
        }
    }

    private void sendWithMappedBuffer(
            PreparedFile preparedFile, SocketChannel socketChannel, int mappedChunkSizeBytes)
            throws IOException {
        try (FileChannel fileChannel = FileChannel.open(preparedFile.path())) {
            long position = 0L;
            while (position < preparedFile.sizeBytes()) {
                long chunkSize = Math.min(mappedChunkSizeBytes, preparedFile.sizeBytes() - position);
                MappedByteBuffer mappedByteBuffer =
                        fileChannel.map(FileChannel.MapMode.READ_ONLY, position, chunkSize);
                while (mappedByteBuffer.hasRemaining()) {
                    socketChannel.write(mappedByteBuffer);
                }
                position += chunkSize;
            }
        }
    }

    private LocalServerContext createLocalServerContextIfNeeded(BenchmarkConfig config)
            throws IOException {
        if (config.executionMode() != ExecutionMode.LOCAL) {
            return LocalServerContext.noop();
        }
        BenchmarkServerConfig serverConfig =
                new BenchmarkServerConfig(
                        config.host(),
                        config.port(),
                        config.bufferSizeBytes(),
                        config.serverOutputDirectory(),
                        config.deleteReceivedFiles());
        TransferServer transferServer = TransferServer.start(serverConfig);
        return new LocalServerContext(transferServer);
    }

    private String resolveHost(BenchmarkConfig config) {
        if (config.executionMode() == ExecutionMode.LOCAL && "0.0.0.0".equals(config.host())) {
            return "127.0.0.1";
        }
        return config.host();
    }

    private int resolvePort(BenchmarkConfig config, LocalServerContext localServerContext)
            throws IOException {
        if (config.executionMode() == ExecutionMode.LOCAL) {
            return localServerContext.port();
        }
        return config.port();
    }

    private static final class LocalServerContext implements AutoCloseable {

        private final TransferServer transferServer;

        private LocalServerContext(TransferServer transferServer) {
            this.transferServer = transferServer;
        }

        static LocalServerContext noop() {
            return new LocalServerContext(null);
        }

        int port() throws IOException {
            if (transferServer == null) {
                throw new IllegalStateException("Local server context is not available");
            }
            return transferServer.port();
        }

        @Override
        public void close() throws IOException {
            if (transferServer != null) {
                transferServer.close();
            }
        }
    }
}
