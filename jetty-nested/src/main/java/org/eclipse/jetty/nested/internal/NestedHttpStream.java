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

package org.eclipse.jetty.nested.internal;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.nested.api.NestedResponse;
import org.eclipse.jetty.server.HttpStream;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.thread.Invocable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NestedHttpStream implements HttpStream
{
    private static final Logger LOG = LoggerFactory.getLogger(NestedHttpStream.class);

    private final NestedEndpoint _endpoint;
    private final NestedConnection _connection;
    private final long _nanoTimestamp = System.nanoTime();
    private final AtomicBoolean _committed = new AtomicBoolean(false);

    public NestedHttpStream(NestedEndpoint endpoint, NestedConnection connection)
    {
        _endpoint = endpoint;
        _connection = connection;
    }

    @Override
    public String getId()
    {
        return _connection.getId();
    }

    @Override
    public long getNanoTimeStamp()
    {
        return _nanoTimestamp;
    }

    @Override
    public Content.Chunk read()
    {
        return _endpoint.getNestedRequest().read();
    }

    @Override
    public void demand()
    {
        _endpoint.getNestedRequest().demand(Invocable.NOOP);
    }

    @Override
    public void prepareResponse(HttpFields.Mutable headers)
    {
        // Do nothing.
    }

    @Override
    public void send(MetaData.Request request, MetaData.Response response, boolean last, ByteBuffer content, Callback callback)
    {
        if (LOG.isDebugEnabled())
            LOG.debug("send() {}, {}, last=={}", request, BufferUtil.toDetailString(content), last);
        _committed.set(true);

        NestedResponse nestedResponse = _endpoint.getNestedResponse();
        if (response != null)
        {
            nestedResponse.setStatus(response.getStatus());
            for (HttpField field : response.getFields())
            {
                nestedResponse.addHeader(field.getName(), field.getValue());
            }
        }

        nestedResponse.write(last, content, callback);
    }

    @Override
    public boolean isPushSupported()
    {
        return false;
    }

    @Override
    public void push(MetaData.Request request)
    {
        throw new UnsupportedOperationException("push not supported");
    }

    @Override
    public boolean isCommitted()
    {
        return _committed.get();
    }

    @Override
    public boolean isComplete()
    {
        return false;
    }

    @Override
    public void setUpgradeConnection(Connection connection)
    {
        throw new UnsupportedOperationException("upgrade not supported");
    }

    @Override
    public Connection upgrade()
    {
        return null;
    }

    @Override
    public void succeeded()
    {
        _endpoint.getNestedResponse().succeeded();
    }

    @Override
    public void failed(Throwable x)
    {
        _endpoint.getNestedResponse().failed(x);
    }
}