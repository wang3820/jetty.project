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

import java.io.IOException;
import java.util.EventListener;

import org.eclipse.jetty.delegate.DelegateConnector;
import org.eclipse.jetty.delegate.api.DelegateExchange;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.server.ConnectionMetaData;
import org.eclipse.jetty.server.internal.HttpChannelState;
import org.eclipse.jetty.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DelegateConnection implements Connection
{
    private static final Logger LOG = LoggerFactory.getLogger(DelegateConnection.class);

    private final DelegateConnector _connector;
    private final DelegateEndpoint _endpoint;
    private final String _connectionId;

    public DelegateConnection(DelegateConnector connector, DelegateEndpoint endpoint)
    {
        _connector = connector;
        _endpoint = endpoint;
        _connectionId = StringUtil.randomAlphaNumeric(16);
    }

    public String getId()
    {
        return _connectionId;
    }

    @Override
    public void addEventListener(EventListener listener)
    {
    }

    @Override
    public void removeEventListener(EventListener listener)
    {
    }

    @Override
    public void onOpen()
    {
        _endpoint.onOpen();
    }

    @Override
    public void onClose(Throwable cause)
    {
    }

    @Override
    public EndPoint getEndPoint()
    {
        return _endpoint;
    }

    @Override
    public void close()
    {
        _endpoint.close();
    }

    @Override
    public boolean onIdleExpired()
    {
        return false;
    }

    @Override
    public long getMessagesIn()
    {
        return 0;
    }

    @Override
    public long getMessagesOut()
    {
        return 0;
    }

    @Override
    public long getBytesIn()
    {
        return 0;
    }

    @Override
    public long getBytesOut()
    {
        return 0;
    }

    @Override
    public long getCreatedTimeStamp()
    {
        return _endpoint.getCreatedTimeStamp();
    }

    public void handle() throws IOException
    {
        DelegateExchange delegateExchange = _endpoint.getDelegateExchange();
        if (LOG.isDebugEnabled())
            LOG.debug("handling request {}", delegateExchange);

        try
        {
            // TODO: We want to recycle the channel instead of creating a new one every time.
            // TODO: Implement the NestedChannel with the top layers HttpChannel.
            ConnectionMetaData connectionMetaData = new DelegateConnectionMetadata(_endpoint, this, _connector);
            HttpChannelState httpChannel = new HttpChannelState(connectionMetaData);

            // TODO: This needs HttpChannel to implement demand().
            httpChannel.setHttpStream(new DelegateHttpStream(_endpoint, this, httpChannel));

            // Generate the Request MetaData.
            String method = delegateExchange.getMethod();
            HttpURI httpURI = HttpURI.build(delegateExchange.getRequestURI());
            HttpVersion httpVersion = HttpVersion.fromString(delegateExchange.getProtocol());
            HttpFields httpFields = delegateExchange.getHeaders();
            long contentLength = (httpFields == null) ? -1 : httpFields.getLongField(HttpHeader.CONTENT_LENGTH);
            MetaData.Request requestMetadata = new MetaData.Request(method, httpURI, httpVersion, httpFields, contentLength);

            // Invoke the HttpChannel.
            Runnable runnable = httpChannel.onRequest(requestMetadata);
            if (LOG.isDebugEnabled())
                LOG.debug("executing channel {}", httpChannel);
            _connector.getExecutor().execute(runnable);
        }
        catch (Throwable t)
        {
            _endpoint.getDelegateExchange().failed(t);
        }
    }
}
