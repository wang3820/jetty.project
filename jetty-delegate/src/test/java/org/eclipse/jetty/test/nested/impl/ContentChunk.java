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

import java.nio.ByteBuffer;
import java.util.Objects;

import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.util.BufferUtil;

public class ContentChunk implements Content.Chunk
{
    private final ByteBuffer byteBuffer;
    private final boolean last;

    public ContentChunk(ByteBuffer byteBuffer)
    {
        this(byteBuffer, true);
    }

    public ContentChunk(byte[] bytes)
    {
        this(BufferUtil.toBuffer(bytes), true);
    }

    public ContentChunk(ByteBuffer byteBuffer, boolean last)
    {
        this.byteBuffer = Objects.requireNonNull(byteBuffer);
        this.last = last;
    }

    @Override
    public ByteBuffer getByteBuffer()
    {
        return byteBuffer;
    }

    @Override
    public boolean isLast()
    {
        return last;
    }

    @Override
    public void retain()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean release()
    {
        return true;
    }

    @Override
    public String toString()
    {
        return "%s@%x[l=%b,b=%s]".formatted(
            getClass().getSimpleName(),
            hashCode(),
            isLast(),
            BufferUtil.toDetailString(getByteBuffer())
        );
    }
}