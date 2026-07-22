package com.portfolio.agent.portfolio.repository.file;

import com.portfolio.agent.portfolio.exception.InvalidPortfolioSnapshotException;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

public final class VectorIndexCodec {

    private static final int MAGIC = 0x43324131;
    private static final int FORMAT_VERSION = 1;
    private static final int MAX_COUNT = 100_000;
    private static final int MAX_ID_BYTES = 1024;

    public byte[] encode(Map<String, float[]> vectors, int dimension) {
        require(dimension > 0 && dimension <= 4096, "vector dimension is invalid");
        require(vectors != null && vectors.size() <= MAX_COUNT, "vector count is invalid");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeInt(output, MAGIC);
        writeInt(output, FORMAT_VERSION);
        writeInt(output, dimension);
        writeInt(output, vectors.size());
        for (Map.Entry<String, float[]> entry : new TreeMap<>(vectors).entrySet()) {
            byte[] id = entry.getKey().getBytes(StandardCharsets.UTF_8);
            require(id.length > 0 && id.length <= MAX_ID_BYTES, "chunkId length is invalid");
            validateVector(entry.getValue(), dimension);
            writeInt(output, id.length);
            output.writeBytes(id);
            for (float value : entry.getValue()) {
                writeInt(output, Float.floatToIntBits(value));
            }
        }
        return output.toByteArray();
    }

    public VectorIndexFile decode(byte[] bytes, int expectedDimension) {
        try {
            ByteBuffer input = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            require(input.remaining() >= 16, "vector index header is truncated");
            require(input.getInt() == MAGIC, "vector index magic is invalid");
            require(input.getInt() == FORMAT_VERSION, "vector index version is invalid");
            int dimension = input.getInt();
            int count = input.getInt();
            require(dimension == expectedDimension, "vector index dimension mismatch");
            require(count >= 0 && count <= MAX_COUNT, "vector index count is invalid");
            Map<String, float[]> vectors = new LinkedHashMap<>();
            for (int item = 0; item < count; item++) {
                require(input.remaining() >= 4, "vector index entry is truncated");
                int idLength = input.getInt();
                require(idLength > 0 && idLength <= MAX_ID_BYTES
                                && input.remaining() >= idLength + dimension * Float.BYTES,
                        "vector index entry bounds are invalid");
                byte[] id = new byte[idLength];
                input.get(id);
                String chunkId = new String(id, StandardCharsets.UTF_8);
                float[] vector = new float[dimension];
                for (int index = 0; index < dimension; index++) {
                    vector[index] = input.getFloat();
                }
                validateVector(vector, dimension);
                require(vectors.put(chunkId, vector) == null,
                        "duplicate vector chunkId: " + chunkId);
            }
            require(!input.hasRemaining(), "vector index contains trailing bytes");
            return new VectorIndexFile(dimension, vectors);
        } catch (InvalidPortfolioSnapshotException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new InvalidPortfolioSnapshotException("vector index is malformed", exception);
        }
    }

    private void validateVector(float[] vector, int dimension) {
        require(vector != null && vector.length == dimension, "vector dimension mismatch");
        double squaredNorm = 0.0;
        for (float value : vector) {
            require(Float.isFinite(value), "vector values must be finite");
            squaredNorm += value * value;
        }
        require(Math.abs(Math.sqrt(squaredNorm) - 1.0) <= 0.001,
                "vector must be L2 normalized");
    }

    private void writeInt(ByteArrayOutputStream output, int value) {
        output.write(value & 0xff);
        output.write((value >>> 8) & 0xff);
        output.write((value >>> 16) & 0xff);
        output.write((value >>> 24) & 0xff);
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new InvalidPortfolioSnapshotException(message);
        }
    }
}
