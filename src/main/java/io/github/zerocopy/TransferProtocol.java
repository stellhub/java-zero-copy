package io.github.zerocopy;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 传输协议编解码。
 */
public final class TransferProtocol {

    private static final int REQUEST_MAGIC = 0x5A434231;
    private static final int RESPONSE_MAGIC = 0x5A434241;

    private TransferProtocol() {}

    /**
     * 写入请求头。
     */
    public static void writeRequestHeader(OutputStream outputStream, TransferRequestHeader header)
            throws IOException {
        DataOutputStream dataOutputStream = new DataOutputStream(outputStream);
        dataOutputStream.writeInt(REQUEST_MAGIC);
        dataOutputStream.writeUTF(header.serverMode().cliValue());
        dataOutputStream.writeUTF(header.transferMode().cliValue());
        dataOutputStream.writeUTF(header.runId());
        dataOutputStream.writeLong(header.expectedBytes());
        dataOutputStream.flush();
    }

    /**
     * 读取请求头。
     */
    public static TransferRequestHeader readRequestHeader(InputStream inputStream)
            throws IOException {
        DataInputStream dataInputStream = new DataInputStream(inputStream);
        int magic = dataInputStream.readInt();
        if (magic != REQUEST_MAGIC) {
            throw new IOException("Invalid request magic: " + magic);
        }
        String serverMode = dataInputStream.readUTF();
        String transferMode = dataInputStream.readUTF();
        String runId = dataInputStream.readUTF();
        long expectedBytes = dataInputStream.readLong();
        return new TransferRequestHeader(
                ServerMode.parseModes(serverMode).iterator().next(),
                TransferMode.parseModes(transferMode).iterator().next(),
                runId,
                expectedBytes);
    }

    /**
     * 写入服务端响应。
     */
    public static void writeResponse(OutputStream outputStream, TransferResponse response)
            throws IOException {
        DataOutputStream dataOutputStream = new DataOutputStream(outputStream);
        dataOutputStream.writeInt(RESPONSE_MAGIC);
        dataOutputStream.writeLong(response.receivedBytes());
        dataOutputStream.writeLong(response.serverElapsedNanos());
        dataOutputStream.flush();
    }

    /**
     * 读取服务端响应。
     */
    public static TransferResponse readResponse(InputStream inputStream) throws IOException {
        DataInputStream dataInputStream = new DataInputStream(inputStream);
        int magic = dataInputStream.readInt();
        if (magic != RESPONSE_MAGIC) {
            throw new IOException("Invalid response magic: " + magic);
        }
        long receivedBytes = dataInputStream.readLong();
        long serverElapsedNanos = dataInputStream.readLong();
        return new TransferResponse(receivedBytes, serverElapsedNanos);
    }
}
