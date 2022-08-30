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

package org.eclipse.jetty.nested;

import java.util.Collections;
import java.util.List;

import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.Connector;

public class NestedConnectionFactory implements ConnectionFactory
{
    private static final String DEFAULT_PROTOCOL = "nested-jetty";
    private final String _protocol;

    public NestedConnectionFactory()
    {
        this(DEFAULT_PROTOCOL);
    }

    public NestedConnectionFactory(String protocol)
    {
        _protocol = protocol;
    }

    @Override
    public String getProtocol()
    {
        return _protocol;
    }

    @Override
    public List<String> getProtocols()
    {
        return Collections.singletonList(_protocol);
    }

    @Override
    public Connection newConnection(Connector connector, EndPoint endPoint)
    {
        return new NestedConnection((NestedConnector)connector, (NestedEndpoint)endPoint);
    }
}
