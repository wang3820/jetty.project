package org.eclipse.jetty.test.nested.rpc;

import java.nio.ByteBuffer;

import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.util.BufferUtil;

public class MockRpcRequest
{
    private final String _method;
    private final String _url;
    private final String _httpVersion;
    private final HttpFields _headers;
    private final byte[] _data;

    public MockRpcRequest(String method, String url, String httpVersion, HttpFields headers, ByteBuffer data)
    {
        _method = method;
        _url = url;
        _httpVersion = httpVersion;
        _headers = headers;
        _data = BufferUtil.toArray(data);
    }

    public byte[] getData()
    {
        return _data;
    }

    public String getMethod()
    {
        return _method;
    }

    public String getHttpVersion()
    {
        return _httpVersion;
    }

    public String getUrl()
    {
        return _url;
    }

    public HttpFields getHeadersList()
    {
        return _headers;
    }

    public String getUserIp()
    {
        return "0.0.0.0";
    }
}
