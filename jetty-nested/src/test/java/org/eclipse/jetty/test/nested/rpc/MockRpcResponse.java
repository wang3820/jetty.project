package org.eclipse.jetty.test.nested.rpc;

import java.nio.ByteBuffer;

import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.util.BufferUtil;

public class MockRpcResponse
{
    private int _statusCode;
    private ByteBuffer _content;
    private final HttpFields.Mutable _fields;

    public MockRpcResponse()
    {
        _fields = HttpFields.build();
    }

    public ByteBuffer getContent()
    {
        return _content;
    }

    public int getStatusCode()
    {
        return _statusCode;
    }

    public HttpFields getFields()
    {
        return _fields.asImmutable();
    }

    public void setHttpResponseCode(int code)
    {
        _statusCode = code;
    }

    public void addHttpOutputHeaders(String name, String value)
    {
        _fields.add(name, value);
    }

    public void setHttpResponseResponse(ByteBuffer content)
    {
        _content = content;
    }

    public void clearHttpResponse()
    {
        _statusCode = 200;
        _fields.clear();
        _content = null;
    }

    public void setErrorMessage(String message)
    {
        _content = BufferUtil.toBuffer(message);
    }

    public void setHttpResponseCodeAndResponse(int code, String string)
    {
        _statusCode = code;
        _content = BufferUtil.toBuffer(string);
    }
}
