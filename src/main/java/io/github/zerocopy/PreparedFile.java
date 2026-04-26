package io.github.zerocopy;

import java.nio.file.Path;

/**
 * 待传输文件信息。
 */
public record PreparedFile(Path path, long sizeBytes, boolean generated) {}
