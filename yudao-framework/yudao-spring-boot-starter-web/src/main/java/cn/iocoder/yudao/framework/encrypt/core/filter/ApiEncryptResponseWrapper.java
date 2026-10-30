package cn.iocoder.yudao.framework.encrypt.core.filter;

import cn.iocoder.yudao.framework.encrypt.core.FieldCrypto;
import cn.iocoder.yudao.framework.encrypt.core.FieldPaths;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

/**
 * 加密响应 {@link HttpServletResponseWrapper} 实现类
 *
 * @author 芋道源码
 */
public class ApiEncryptResponseWrapper extends HttpServletResponseWrapper {

    private final ByteArrayOutputStream byteArrayOutputStream;
    private final ServletOutputStream servletOutputStream;
    private final PrintWriter printWriter;

    public ApiEncryptResponseWrapper(HttpServletResponse response) {
        super(response);
        this.byteArrayOutputStream = new ByteArrayOutputStream();
        this.servletOutputStream = this.getOutputStream();
        this.printWriter = new PrintWriter(new OutputStreamWriter(byteArrayOutputStream, java.nio.charset.StandardCharsets.UTF_8));
    }

    public void encrypt(FieldCrypto crypto,
                        String[] fields) throws IOException {
        HttpServletResponse response = (HttpServletResponse) this.getResponse();
        this.flushBuffer();
        byte[] body = byteArrayOutputStream.toByteArray();
        if (body.length == 0) return;
        String contentType = getContentType();
        if (contentType == null || !(contentType.toLowerCase(java.util.Locale.ROOT).contains("application/json")
                || contentType.toLowerCase(java.util.Locale.ROOT).contains("+json"))) {
            throw new IllegalArgumentException("字段加密仅支持 JSON 响应");
        }
        byte[] encrypted = FieldPaths.transform(body, fields, crypto::encrypt);
        response.resetBuffer();
        response.setCharacterEncoding("UTF-8");
        response.setContentLength(encrypted.length);
        response.getOutputStream().write(encrypted);
    }

    @Override
    public PrintWriter getWriter() {
        return printWriter;
    }

    @Override
    public void flushBuffer() throws IOException {
        if (servletOutputStream != null) {
            servletOutputStream.flush();
        }
        if (printWriter != null) {
            printWriter.flush();
        }
    }

    @Override
    public void reset() {
        super.reset();
        byteArrayOutputStream.reset();
    }

    @Override
    public void resetBuffer() {
        byteArrayOutputStream.reset();
    }

    @Override
    public void setContentLength(int length) { }

    @Override
    public void setContentLengthLong(long length) { }

    @Override
    public ServletOutputStream getOutputStream() {
        return new ServletOutputStream() {

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener writeListener) {
            }

            @Override
            public void write(int b) {
                byteArrayOutputStream.write(b);
            }

            @Override
            @SuppressWarnings("NullableProblems")
            public void write(byte[] b) throws IOException {
                byteArrayOutputStream.write(b);
            }

            @Override
            @SuppressWarnings("NullableProblems")
            public void write(byte[] b, int off, int len) {
                byteArrayOutputStream.write(b, off, len);
            }

        };
    }

}