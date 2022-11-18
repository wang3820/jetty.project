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

package org.eclipse.jetty.delegate.internal;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jetty.delegate.api.DelegateExchange;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.HttpChannel;
import org.eclipse.jetty.server.HttpStream;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DelegateHttpStream implements HttpStream
{
    private static final Logger LOG = LoggerFactory.getLogger(DelegateHttpStream.class);

    private final DelegateEndpoint _endpoint;
    private final DelegateConnection _connection;
    private final HttpChannel _httpChannel;
    private final long _nanoTimestamp = System.nanoTime();
    private final AtomicBoolean _committed = new AtomicBoolean(false);

    public DelegateHttpStream(DelegateEndpoint endpoint, DelegateConnection connection, HttpChannel httpChannel)
    {
        _endpoint = endpoint;
        _connection = connection;
        _httpChannel = httpChannel;
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
        return _endpoint.getDelegateExchange().read();
    }

    @Override
    public void demand()
    {
        _endpoint.getDelegateExchange().demand(_httpChannel::onContentAvailable);
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

        DelegateExchange delegateExchange = _endpoint.getDelegateExchange();
        if (response != null)
        {
            delegateExchange.setStatus(response.getStatus());
            for (HttpField field : response.getFields())
            {
                delegateExchange.addHeader(field.getName(), field.getValue());
            }
        }

        delegateExchange.write(last, content, callback);
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
    public void succeeded()
    {
        _endpoint.getDelegateExchange().succeeded();
    }

    @Override
    public void failed(Throwable x)
    {
        _endpoint.getDelegateExchange().failed(x);
    }
}