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

import java.io.IOException;

import org.eclipse.jetty.nested.api.NestedRequest;
import org.eclipse.jetty.nested.api.NestedResponse;
import org.eclipse.jetty.nested.internal.NestedConnection;
import org.eclipse.jetty.nested.internal.NestedConnectionFactory;
import org.eclipse.jetty.nested.internal.NestedEndpoint;
import org.eclipse.jetty.server.AbstractConnector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.Server;

public class NestedConnector extends AbstractConnector
{
    private final HttpConfiguration _httpConfiguration = new HttpConfiguration();

    public NestedConnector(Server server)
    {
        this(server, null);
    }

    public NestedConnector(Server server, String protocol)
    {
        super(server, null, null, null, 0, new NestedConnectionFactory(protocol));
        _httpConfiguration.setSendDateHeader(false);
        _httpConfiguration.setSendServerVersion(false);
        _httpConfiguration.setSendXPoweredBy(false);
    }

    public HttpConfiguration getHttpConfiguration()
    {
        return _httpConfiguration;
    }

    public void service(NestedRequest request, NestedResponse response) throws IOException
    {
        // TODO: recover existing endpoint and connection from WeakReferenceMap with request as key, or some other way of
        //  doing persistent connection. There is a proposal in the servlet spec to have connection IDs.
        NestedEndpoint endPoint = new NestedEndpoint(request, response);
        NestedConnection connection = new NestedConnection(this, endPoint);
        connection.handle();
    }

    @Override
    public Object getTransport()
    {
        return null;
    }

    @Override
    protected void accept(int acceptorID) throws UnsupportedOperationException
    {
        throw new UnsupportedOperationException("Accept not supported by this Connector");
    }
}
