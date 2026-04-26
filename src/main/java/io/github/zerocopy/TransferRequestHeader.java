package io.github.zerocopy;

/**
 * 传输请求头。
 */
public record TransferRequestHeader(
        ServerMode serverMode, TransferMode transferMode, String runId, long expectedBytes) {}
