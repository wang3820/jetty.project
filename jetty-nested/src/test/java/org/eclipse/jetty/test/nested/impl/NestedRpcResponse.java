package org.eclipse.jetty.test.nested.impl;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.eclipse.jetty.io.ByteBufferAccumulator;
import org.eclipse.jetty.nested.api.NestedResponse;
import org.eclipse.jetty.test.nested.rpc.MockRpcResponse;
import org.eclipse.jetty.util.Callback;

public class NestedRpcResponse implements NestedResponse
{
    private final MockRpcResponse _response;
    private final ByteBufferAccumulator accumulator = new ByteBufferAccumulator();

    private final CompletableFuture<Void> _completion = new CompletableFuture<>();

    public NestedRpcResponse(MockRpcResponse response)
    {
        _response = response;
    }

    @Override
    public void setStatus(int status)
    {
        _response.setHttpResponseCode(status);
    }

    @Override
    public void addHeader(String name, String value)
    {
        _response.addHttpOutputHeaders(name, value);
    }

    @Override
    public void write(boolean last, ByteBuffer content, Callback callback)
    {
        accumulator.copyBuffer(content);
        callback.succeeded();
    }

    @Override
    public void succeeded()
    {
        _response.setHttpResponseResponse(accumulator.toByteBuffer());
        _completion.complete(null);
    }

    @Override
    public void failed(Throwable x)
    {
        _completion.completeExceptionally(x);
    }

    public void awaitResponse() throws ExecutionException, InterruptedException
    {
        _completion.get();
    }
}
