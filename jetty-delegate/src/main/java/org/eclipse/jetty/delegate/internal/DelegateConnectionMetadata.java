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

import java.net.SocketAddress;

import org.eclipse.jetty.delegate.DelegateConnector;
import org.eclipse.jetty.delegate.api.DelegateExchange;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.server.ConnectionMetaData;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.util.Attributes;
import org.eclipse.jetty.util.HostPort;

public class DelegateConnectionMetadata extends Attributes.Lazy implements ConnectionMetaData
{
    private final DelegateExchange _exchange;
    private final DelegateConnection _connection;
    private final String _connectionId;
    private final HttpConfiguration _httpConfiguration;
    private final DelegateConnector _connector;

    public DelegateConnectionMetadata(DelegateEndpoint delegateEndpoint, DelegateConnection delegateConnection, DelegateConnector delegateConnector)
    {
        _exchange = delegateEndpoint.getDelegateExchange();
        _connectionId = delegateConnection.getId();
        _connector = delegateConnector;
        _httpConfiguration = delegateConnector.getHttpConfiguration();
        _connection = delegateConnection;
    }

    @Override
    public String getId()
    {
        return _connectionId;
    }

    @Override
    public HttpConfiguration getHttpConfiguration()
    {
        return _httpConfiguration;
    }

    @Override
    public HttpVersion getHttpVersion()
    {
        return HttpVersion.fromString(_exchange.getProtocol());
    }

    @Override
    public String getProtocol()
    {
        return _exchange.getProtocol();
    }

    @Override
    public Connection getConnection()
    {
        return _connection;
    }

    @Override
    public Connector getConnector()
    {
        return _connector;
    }

    @Override
    public boolean isPersistent()
    {
        return false;
    }

    @Override
    public boolean isSecure()
    {
        return false;
    }

    @Override
    public SocketAddress getRemoteSocketAddress()
    {
        return _exchange.getRemoteAddr();
    }

    @Override
    public SocketAddress getLocalSocketAddress()
    {
        return _exchange.getLocalAddr();
    }

    @Override
    public HostPort getServerAuthority()
    {
        HostPort authority = ConnectionMetaData.getServerAuthority(getHttpConfiguration(), this);
        if (authority == null)
            authority = new HostPort(getLocalSocketAddress().toString(), -1);
        return authority;
    }
}
