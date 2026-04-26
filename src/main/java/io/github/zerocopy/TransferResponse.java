package io.github.zerocopy;

/**
 * 服务端传输响应。
 */
public record TransferResponse(long receivedBytes, long serverElapsedNanos) {}
