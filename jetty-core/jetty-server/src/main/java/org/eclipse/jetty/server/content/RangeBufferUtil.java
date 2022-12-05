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

package org.eclipse.jetty.server.content;

import java.nio.ByteBuffer;
import java.util.List;

import org.eclipse.jetty.http.ByteRange;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.MultiPart;
import org.eclipse.jetty.http.MultiPartByteRanges;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;

import static org.eclipse.jetty.server.content.ResourceService.NO_CONTENT_LENGTH;
import static org.eclipse.jetty.server.content.ResourceService.USE_KNOWN_CONTENT_LENGTH;

class RangeBufferUtil
{
    private RangeBufferUtil()
    {
    }

    public static void writeBuffer(HttpContent content, ResourceService resourceService, ByteBuffer byteBuffer, Request request, Response response, Callback callback)
    {
        long contentLength = content.getContentLengthValue();
        callback = Callback.from(callback, content::release);

        // Is this a Range request?
        List<String> reqRanges = request.getHeaders().getValuesList(HttpHeader.RANGE.asString());
        if (reqRanges.isEmpty())
        {
            // If there are no ranges, send the entire content.
            if (contentLength >= 0)
                resourceService.putHeaders(response, content, USE_KNOWN_CONTENT_LENGTH);
            else
                resourceService.putHeaders(response, content, NO_CONTENT_LENGTH);
            response.write(true, byteBuffer, callback);
            return;
        }

        // Parse the satisfiable ranges.
        List<ByteRange> ranges = ByteRange.parse(reqRanges, contentLength);

        // If there are no satisfiable ranges, send a 416 response.
        if (ranges.isEmpty())
        {
            resourceService.putHeaders(response, content, NO_CONTENT_LENGTH);
            response.getHeaders().put(HttpHeader.CONTENT_RANGE, ByteRange.toNonSatisfiableHeaderValue(contentLength));
            resourceService.writeHttpError(request, response, callback, HttpStatus.RANGE_NOT_SATISFIABLE_416);
            return;
        }

        // If there is only a single valid range, send that range with a 206 response.
        if (ranges.size() == 1)
        {
            ByteRange range = ranges.get(0);
            resourceService.putHeaders(response, content, range.getLength());
            response.setStatus(HttpStatus.PARTIAL_CONTENT_206);
            response.getHeaders().put(HttpHeader.CONTENT_RANGE, range.toHeaderValue(contentLength));
            Content.copy(new MultiPartByteRanges.ByteBufferContentSource(byteBuffer, range), response, callback);
            return;
        }

        // There are multiple non-overlapping ranges, send a multipart/byteranges 206 response.
        resourceService.putHeaders(response, content, NO_CONTENT_LENGTH);
        response.setStatus(HttpStatus.PARTIAL_CONTENT_206);
        String contentType = "multipart/byteranges; boundary=";
        String boundary = MultiPart.generateBoundary(null, 24);
        response.getHeaders().put(HttpHeader.CONTENT_TYPE, contentType + boundary);
        MultiPartByteRanges.ContentSource byteRanges = new MultiPartByteRanges.ContentSource(boundary);
        ranges.forEach(range -> byteRanges.addPart(new MultiPartByteRanges.Part(content.getContentTypeValue(), byteBuffer, range, contentLength)));
        byteRanges.close();
        Content.copy(byteRanges, response, callback);
    }
}
