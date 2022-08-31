package org.eclipse.jetty.test.nested.impl;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.io.internal.ByteBufferChunk;
import org.eclipse.jetty.nested.api.NestedRequest;
import org.eclipse.jetty.test.nested.rpc.MockRpcRequest;
import org.eclipse.jetty.util.BufferUtil;

public class NestedRpcRequest implements NestedRequest
{
    private static final Content.Chunk EOF = Content.Chunk.EOF;
    private final MockRpcRequest _request;
    private final AtomicReference<Content.Chunk> _content = new AtomicReference<>();

    public NestedRpcRequest(MockRpcRequest request)
    {
        _request = request;
        _content.set(new ByteBufferChunk(BufferUtil.toBuffer(request.getData()), true){});
    }

    @Override
    public String getRequestURI()
    {
        return _request.getUrl();
    }

    @Override
    public String getProtocol()
    {
        return _request.getHttpVersion();
    }

    @Override
    public String getMethod()
    {
        return _request.getMethod();
    }

    @Override
    public HttpFields getHeaders()
    {
        return _request.getHeadersList();
    }

    @Override
    public InetSocketAddress getRemoteAddr()
    {
        return InetSocketAddress.createUnresolved(_request.getUserIp(), 0);
    }

    @Override
    public InetSocketAddress getLocalAddr()
    {
        return InetSocketAddress.createUnresolved("0.0.0.0", 0);
    }

    @Override
    public Content.Chunk read()
    {
        return _content.getAndUpdate(chunk -> (chunk instanceof ByteBufferChunk) ? EOF : chunk);
    }

    @Override
    public void demand(Runnable demandCallback)
    {
        demandCallback.run();
    }

    @Override
    public void fail(Throwable failure)
    {
        _content.set(Content.Chunk.from(failure));
    }
}
