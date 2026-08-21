package com.portfolio.agent.common.web;

import com.portfolio.agent.common.observability.FrontendDiagnosticProperties;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public final class FrontendDiagnosticsBodyLimitFilter extends OncePerRequestFilter {

    private static final String INGEST_PATH = "/api/client-diagnostics";

    private final FrontendDiagnosticProperties properties;

    public FrontendDiagnosticsBodyLimitFilter() {
        this(new FrontendDiagnosticProperties());
    }

    @Autowired(required = false)
    public FrontendDiagnosticsBodyLimitFilter(FrontendDiagnosticProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            jakarta.servlet.FilterChain filterChain
    ) throws ServletException, IOException {
        if (!INGEST_PATH.equals(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!properties.isFrontendIngestEnabled()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        int maxBodyBytes = properties.getFrontendMaxBodyBytes();
        if (request.getContentLengthLong() > maxBodyBytes) {
            reject(response);
            return;
        }
        byte[] body = readAtMost(request.getInputStream(), maxBodyBytes);
        if (body.length > maxBodyBytes) {
            reject(response);
            return;
        }
        filterChain.doFilter(new CachedBodyRequestWrapper(request, body), response);
    }

    private byte[] readAtMost(
            ServletInputStream inputStream,
            int maxBodyBytes
    ) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream(
                Math.min(maxBodyBytes, 8_192));
        byte[] buffer = new byte[8_192];
        while (body.size() <= maxBodyBytes) {
            int remaining = maxBodyBytes - body.size() + 1;
            int count = inputStream.read(
                    buffer, 0, Math.min(buffer.length, remaining));
            if (count == -1) {
                break;
            }
            if (count > 0) {
                body.write(buffer, 0, count);
            }
        }
        return body.toByteArray();
    }

    private void reject(HttpServletResponse response) {
        if (!response.isCommitted()) {
            response.resetBuffer();
            response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        }
    }

    private static final class CachedBodyRequestWrapper extends HttpServletRequestWrapper {

        private final byte[] body;
        private CachedServletInputStream inputStream;

        private CachedBodyRequestWrapper(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body.clone();
        }

        @Override
        public ServletInputStream getInputStream() {
            if (inputStream == null) {
                inputStream = new CachedServletInputStream(body);
            }
            return inputStream;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            return new BufferedReader(new InputStreamReader(
                    getInputStream(), StandardCharsets.UTF_8));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }
    }

    private static final class CachedServletInputStream extends ServletInputStream {

        private final ByteArrayInputStream delegate;

        private CachedServletInputStream(byte[] body) {
            this.delegate = new ByteArrayInputStream(body);
        }

        @Override
        public int read() {
            return delegate.read();
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            return delegate.read(buffer, offset, length);
        }

        @Override
        public boolean isFinished() {
            return delegate.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            Objects.requireNonNull(readListener, "readListener must not be null");
            try {
                if (!isFinished()) {
                    readListener.onDataAvailable();
                }
                if (isFinished()) {
                    readListener.onAllDataRead();
                }
            } catch (IOException exception) {
                readListener.onError(exception);
            }
        }
    }
}
