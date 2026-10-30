package cn.iocoder.yudao.framework.encrypt.core.filter;

import cn.iocoder.yudao.framework.encrypt.core.FieldCrypto;
import cn.iocoder.yudao.framework.encrypt.core.FieldPaths;

import cn.hutool.core.io.IoUtil;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * 解密请求 {@link HttpServletRequestWrapper} 实现类
 *
 * @author 芋道源码
 */
public class ApiDecryptRequestWrapper extends HttpServletRequestWrapper {

    private final byte[] body;

    public ApiDecryptRequestWrapper(HttpServletRequest request,
                                    FieldCrypto crypto,
                                    String[] fields) throws IOException {
        super(request);
        body = FieldPaths.transform(
                IoUtil.readBytes(request.getInputStream(), false), fields, crypto::decrypt);
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(this.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
    }

    @Override
    public int getContentLength() {
        return body.length;
    }

    @Override
    public long getContentLengthLong() {
        return body.length;
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream stream = new ByteArrayInputStream(body);
        return new ServletInputStream() {

            @Override
            public int read() {
                return stream.read();
            }

            @Override
            public int available() {
                return stream.available();
            }

            @Override
            public boolean isFinished() {
                return stream.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
            }

        };
    }
}
