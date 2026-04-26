package io.github.zerocopy;

import java.util.concurrent.CountDownLatch;

/**
 * 独立服务端启动入口。
 */
public final class ZeroCopyBenchmarkServerApplication {

    private ZeroCopyBenchmarkServerApplication() {}

    /**
     * 启动独立服务端。
     */
    public static void main(String[] args) throws Exception {
        BenchmarkServerConfig serverConfig = BenchmarkServerConfig.fromArgs(args);
        TransferServer transferServer = TransferServer.start(serverConfig);
        Runtime.getRuntime()
                .addShutdownHook(
                        new Thread(
                                () -> {
                                    try {
                                        transferServer.close();
                                    } catch (Exception ignored) {
                                    }
                                }));
        System.out.println("=== Java Zero Copy Benchmark Server ===");
        System.out.println(serverConfig.describe());
        System.out.println("listeningPort=" + transferServer.port());
        new CountDownLatch(1).await();
    }
}
