package io.github.zerocopy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 独立传输服务端。
 */
public final class TransferServer implements AutoCloseable {

    private final BenchmarkServerConfig serverConfig;
    private final ServerSocketChannel serverSocketChannel;
    private final ExecutorService acceptExecutor;
    private final ExecutorService workerExecutor;
    private final AtomicBoolean running;

    private TransferServer(
            BenchmarkServerConfig serverConfig,
            ServerSocketChannel serverSocketChannel,
            ExecutorService acceptExecutor,
            ExecutorService workerExecutor) {
        this.serverConfig = serverConfig;
        this.serverSocketChannel = serverSocketChannel;
        this.acceptExecutor = acceptExecutor;
        this.workerExecutor = workerExecutor;
        this.running = new AtomicBoolean(true);
    }

    /**
     * 启动服务端。
     */
    public static TransferServer start(BenchmarkServerConfig serverConfig) throws IOException {
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress(serverConfig.host(), serverConfig.port()));
        ExecutorService acceptExecutor =
                Executors.newSingleThreadExecutor(
                        runnable -> {
                            Thread thread = new Thread(runnable, "transfer-server-acceptor");
                            thread.setDaemon(true);
                            return thread;
                        });
        ExecutorService workerExecutor =
                Executors.newCachedThreadPool(
                        runnable -> {
                            Thread thread = new Thread(runnable, "transfer-server-worker");
                            thread.setDaemon(true);
                            return thread;
                        });
        TransferServer transferServer =
                new TransferServer(serverConfig, serverSocketChannel, acceptExecutor, workerExecutor);
        transferServer.startAcceptLoop();
        return transferServer;
    }

    /**
     * 获取实际监听端口。
     */
    public int port() throws IOException {
        return ((InetSocketAddress) serverSocketChannel.getLocalAddress()).getPort();
    }

    @Override
    public void close() throws IOException {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        serverSocketChannel.close();
        acceptExecutor.shutdownNow();
        workerExecutor.shutdownNow();
    }

    private void startAcceptLoop() {
        acceptExecutor.submit(
                () -> {
                    while (running.get()) {
                        try {
                            SocketChannel clientChannel = serverSocketChannel.accept();
                            workerExecutor.submit(() -> handleClient(clientChannel));
                        } catch (IOException exception) {
                            if (running.get()) {
                                exception.printStackTrace(System.err);
                            }
                            return;
                        }
                    }
                });
    }

    private void handleClient(SocketChannel clientChannel) {
        try (SocketChannel channel = clientChannel) {
            channel.setOption(StandardSocketOptions.TCP_NODELAY, true);
            TransferRequestHeader header =
                    TransferProtocol.readRequestHeader(channel.socket().getInputStream());
            Path receivedFilePath = createReceivedFilePath(header);
            long startNanos = System.nanoTime();
            long receivedBytes =
                    receivePayload(channel, header.serverMode(), receivedFilePath);
            long elapsedNanos = System.nanoTime() - startNanos;
            TransferProtocol.writeResponse(
                    channel.socket().getOutputStream(),
                    new TransferResponse(receivedBytes, elapsedNanos));
            cleanupReceivedFile(receivedFilePath);
        } catch (Exception exception) {
            exception.printStackTrace(System.err);
        }
    }

    private long receivePayload(SocketChannel clientChannel, ServerMode serverMode, Path receivedFilePath)
            throws IOException {
        ByteBuffer buffer = ByteBuffer.allocateDirect(serverConfig.bufferSizeBytes());
        long totalBytes = 0L;
        if (serverMode == ServerMode.DISK) {
            Files.createDirectories(receivedFilePath.getParent());
            try (FileChannel fileChannel =
                    FileChannel.open(
                            receivedFilePath,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING,
                            StandardOpenOption.WRITE)) {
                while (true) {
                    int readBytes = clientChannel.read(buffer);
                    if (readBytes == -1) {
                        fileChannel.force(false);
                        return totalBytes;
                    }
                    totalBytes += readBytes;
                    buffer.flip();
                    while (buffer.hasRemaining()) {
                        fileChannel.write(buffer);
                    }
                    buffer.clear();
                }
            }
        }

        while (true) {
            int readBytes = clientChannel.read(buffer);
            if (readBytes == -1) {
                return totalBytes;
            }
            totalBytes += readBytes;
            buffer.clear();
        }
    }

    private Path createReceivedFilePath(TransferRequestHeader header) {
        return serverConfig.normalizedOutputDirectory()
                .resolve(
                        "%s-%s-%s.bin"
                                .formatted(
                                        header.serverMode().cliValue(),
                                        header.transferMode().cliValue(),
                                        CliSupport.sanitizeToken(header.runId())));
    }

    private void cleanupReceivedFile(Path receivedFilePath) throws IOException {
        if (serverConfig.deleteReceivedFiles()) {
            Files.deleteIfExists(receivedFilePath);
        }
    }
}
