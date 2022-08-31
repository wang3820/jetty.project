//
// ========================================================================
// Copyright (c) 1995-2022 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under the
// terms of the Eclipse Public License v. 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
// which is available at https://www.apache.org/licenses/LICENSE-2.0.
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package org.eclipse.jetty.test.nested.impl;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.nested.api.NestedRequest;
import org.eclipse.jetty.test.nested.rpc.MockRpcRequest;

public class NestedRpcRequest implements NestedRequest
{
    private static final Content.Chunk EOF = Content.Chunk.EOF;
    private final MockRpcRequest _request;
    private final AtomicReference<Content.Chunk> _content = new AtomicReference<>();

    public NestedRpcRequest(MockRpcRequest request)
    {
        _request = request;
        _content.set(new ContentChunk(request.getData()));
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
        return _content.getAndUpdate(chunk -> (chunk instanceof ContentChunk) ? EOF : chunk);
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
