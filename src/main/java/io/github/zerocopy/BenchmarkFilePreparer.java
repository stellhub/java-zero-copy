package io.github.zerocopy;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * 准备测试文件。
 */
public final class BenchmarkFilePreparer {

    private static final int FILE_WRITE_BUFFER_SIZE = 1024 * 1024;

    private BenchmarkFilePreparer() {}

    /**
     * 准备待传输文件，不存在时自动生成。
     */
    public static PreparedFile prepare(BenchmarkConfig config) throws IOException {
        Path filePath = config.filePath().toAbsolutePath().normalize();
        if (Files.exists(filePath)) {
            return new PreparedFile(filePath, Files.size(filePath), false);
        }
        Path parent = filePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        createDataFile(filePath, config.fileSizeBytes());
        return new PreparedFile(filePath, config.fileSizeBytes(), true);
    }

    /**
     * 删除自动生成的临时文件。
     */
    public static void deleteIfGenerated(BenchmarkConfig config, PreparedFile preparedFile)
            throws IOException {
        if (config.deleteGeneratedFile() && preparedFile.generated()) {
            Files.deleteIfExists(preparedFile.path());
        }
    }

    private static void createDataFile(Path filePath, long sizeBytes) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(FILE_WRITE_BUFFER_SIZE);
        long written = 0L;
        byte seed = 0;
        try (FileChannel fileChannel =
                FileChannel.open(
                        filePath,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE)) {
            while (written < sizeBytes) {
                buffer.clear();
                int chunkSize = (int) Math.min(buffer.capacity(), sizeBytes - written);
                for (int index = 0; index < chunkSize; index++) {
                    buffer.put(seed++);
                }
                buffer.flip();
                fileChannel.write(buffer);
                written += chunkSize;
            }
            fileChannel.force(true);
        }
    }
}
