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

package org.eclipse.jetty.test.nested.rpc;

import java.nio.ByteBuffer;

import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.util.BufferUtil;

public class MockRpcResponse
{
    private int _statusCode;
    private ByteBuffer _content;
    private final HttpFields.Mutable _fields;

    public MockRpcResponse()
    {
        _fields = HttpFields.build();
    }

    public ByteBuffer getContent()
    {
        return _content;
    }

    public int getStatusCode()
    {
        return _statusCode;
    }

    public HttpFields getFields()
    {
        return _fields.asImmutable();
    }

    public void setHttpResponseCode(int code)
    {
        _statusCode = code;
    }

    public void addHttpOutputHeaders(String name, String value)
    {
        _fields.add(name, value);
    }

    public void setHttpResponseResponse(ByteBuffer content)
    {
        _content = content;
    }

    public void clearHttpResponse()
    {
        _statusCode = 200;
        _fields.clear();
        _content = null;
    }

    public void setErrorMessage(String message)
    {
        _content = BufferUtil.toBuffer(message);
    }

    public void setHttpResponseCodeAndResponse(int code, String string)
    {
        _statusCode = code;
        _content = BufferUtil.toBuffer(string);
    }
}
