//
// ========================================================================
// Copyright (c) 1995 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under the
// terms of the Eclipse Public License v. 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
// which is available at https://www.apache.org/licenses/LICENSE-2.0.
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package org.eclipse.jetty.http;

import java.nio.ByteBuffer;

import org.eclipse.jetty.util.BufferUtil;

import static java.nio.charset.StandardCharsets.ISO_8859_1;

/**
 *
 */
public abstract class Http1FieldPreEncoder implements HttpFieldPreEncoder
{
    @Override
    public ByteBuffer getEncodedField(HttpHeader header, String headerString, String value)
    {
        if (header != null)
        {
            int cbl = header.getBytesColonSpace().length;
            ByteBuffer buffer = ByteBuffer.allocateDirect(cbl + value.length() + 2);
            BufferUtil.clearToFill(buffer);
            buffer.put(header.getBytesColonSpace());
            buffer.put(value.getBytes(ISO_8859_1));
            buffer.put((byte)'\r');
            buffer.put((byte)'\n');
            BufferUtil.flipToFlush(buffer, 0);
            return buffer;
        }

        byte[] n = headerString.getBytes(ISO_8859_1);
        byte[] v = value.getBytes(ISO_8859_1);
        ByteBuffer buffer = ByteBuffer.allocateDirect(n.length + 2 + v.length + 2);
        BufferUtil.clearToFill(buffer);
        buffer.put(n);
        buffer.put((byte)':');
        buffer.put((byte)' ');
        buffer.put(v);
        buffer.put((byte)'\r');
        buffer.put((byte)'\n');
        BufferUtil.flipToFlush(buffer, 0);
        return buffer;
    }
}
