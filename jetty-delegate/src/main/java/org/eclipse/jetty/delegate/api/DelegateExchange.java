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

package org.eclipse.jetty.delegate.api;

import java.net.InetSocketAddress;

import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.util.Callback;

public interface DelegateExchange extends Content.Source, Content.Sink, Callback
{
    // Request Methods.

    String getRequestURI();

    String getProtocol();

    String getMethod();

    HttpFields getHeaders();

    InetSocketAddress getRemoteAddr();

    InetSocketAddress getLocalAddr();

    // Response Methods

    void setStatus(int status);

    void addHeader(String name, String value);
}
