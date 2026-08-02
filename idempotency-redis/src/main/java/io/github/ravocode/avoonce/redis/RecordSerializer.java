package io.github.ravocode.avoonce.redis;

import io.github.ravocode.avoonce.core.domain.IdempotencyRecord;
import io.github.ravocode.avoonce.core.domain.IdempotencyResponse;
import io.github.ravocode.avoonce.core.domain.IdempotencyStatus;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A lightweight, zero-dependency binary serializer for {@link IdempotencyRecord}.
 * <p>
 * The serialization format is versioned: the first byte of every payload is a
 * version marker. This allows future format changes to be detected and handled
 * gracefully rather than silently corrupting data.
 */
class RecordSerializer {

    /** Current serialization format version. */
    private static final byte SERIALIZATION_VERSION = 1;

    private RecordSerializer() {
    }

    /**
     * Serializes an {@link IdempotencyRecord} into a versioned binary byte array.
     *
     * @param record the record to serialize; may be {@code null}.
     * @return the serialized byte array, or an empty byte array if record is {@code null}.
     */
    public static byte[] serialize(IdempotencyRecord record) {
        if (record == null) {
            return new byte[0];
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(baos)) {

            out.writeByte(SERIALIZATION_VERSION);
            writeString(out, record.getIdempotencyKey());
            writeString(out, record.getStatus() != null ? record.getStatus().name() : null);
            writeString(out, record.getRequestHash());
            
            if (record.getExpiresAt() != null) {
                out.writeBoolean(true);
                out.writeLong(record.getExpiresAt());
            } else {
                out.writeBoolean(false);
            }

            if (record.getResponse() != null) {
                out.writeBoolean(true);
                out.writeInt(record.getResponse().getStatusCode());
                
                Map<String, List<String>> headers = record.getResponse().getHeaders();
                if (headers != null) {
                    out.writeInt(headers.size());
                    for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                        writeString(out, entry.getKey());
                        List<String> values = entry.getValue();
                        if (values != null) {
                            out.writeInt(values.size());
                            for (String v : values) {
                                writeString(out, v);
                            }
                        } else {
                            out.writeInt(0);
                        }
                    }
                } else {
                    out.writeInt(0);
                }

                byte[] body = record.getResponse().getBody();
                if (body != null) {
                    out.writeInt(body.length);
                    out.write(body);
                } else {
                    out.writeInt(-1);
                }
            } else {
                out.writeBoolean(false);
            }

            out.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize IdempotencyRecord", e);
        }
    }

    /**
     * Deserializes a versioned binary byte array into an {@link IdempotencyRecord}.
     *
     * @param data the raw byte array to deserialize; may be {@code null} or empty.
     * @return the deserialized {@link IdempotencyRecord}, or {@code null} if data is empty.
     * @throws IllegalStateException if the serialization version is unsupported or data is corrupt.
     */
    public static IdempotencyRecord deserialize(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             DataInputStream in = new DataInputStream(bais)) {

            byte version = in.readByte();
            if (version != SERIALIZATION_VERSION) {
                throw new IllegalStateException(
                        "Unsupported serialization version: " + version
                                + " (expected " + SERIALIZATION_VERSION + ")");
            }

            String key = readString(in);
            String statusStr = readString(in);
            IdempotencyStatus status = statusStr != null ? IdempotencyStatus.valueOf(statusStr) : null;
            String requestHash = readString(in);

            Long expiresAt = null;
            if (in.readBoolean()) {
                expiresAt = in.readLong();
            }

            IdempotencyResponse response = null;
            if (in.readBoolean()) {
                int statusCode = in.readInt();
                
                int headersSize = in.readInt();
                Map<String, List<String>> headers = new HashMap<>();
                for (int i = 0; i < headersSize; i++) {
                    String hKey = readString(in);
                    int valuesSize = in.readInt();
                    List<String> values = new ArrayList<>();
                    for (int j = 0; j < valuesSize; j++) {
                        values.add(readString(in));
                    }
                    headers.put(hKey, values);
                }

                int bodyLen = in.readInt();
                byte[] body = null;
                if (bodyLen >= 0) {
                    body = new byte[bodyLen];
                    in.readFully(body);
                }
                
                response = new IdempotencyResponse(statusCode, headers, body);
            }

            return new IdempotencyRecord(key, status, response, expiresAt, requestHash);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to deserialize IdempotencyRecord", e);
        }
    }

    private static void writeString(DataOutputStream out, String s) throws IOException {
        if (s == null) {
            out.writeInt(-1);
        } else {
            byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
            out.writeInt(bytes.length);
            out.write(bytes);
        }
    }

    private static String readString(DataInputStream in) throws IOException {
        int len = in.readInt();
        if (len == -1) {
            return null;
        }
        byte[] bytes = new byte[len];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
