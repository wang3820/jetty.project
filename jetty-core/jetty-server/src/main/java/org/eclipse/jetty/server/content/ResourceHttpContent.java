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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.eclipse.jetty.http.ByteRange;
import org.eclipse.jetty.http.DateGenerator;
import org.eclipse.jetty.http.EtagUtils;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.http.MimeTypes.Type;
import org.eclipse.jetty.http.MultiPart;
import org.eclipse.jetty.http.MultiPartByteRanges;
import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.IO;
import org.eclipse.jetty.util.IteratingCallback;
import org.eclipse.jetty.util.resource.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.eclipse.jetty.server.content.ResourceService.NO_CONTENT_LENGTH;
import static org.eclipse.jetty.server.content.ResourceService.USE_KNOWN_CONTENT_LENGTH;

/**
 * HttpContent created from a {@link Resource}.
 * <p>The HttpContent is used to server static content that is not
 * cached. So fields and values are only generated as need be an not
 * kept for reuse</p>
 */
public class ResourceHttpContent implements HttpContent
{
    private static final Logger LOG = LoggerFactory.getLogger(ResourceHttpContent.class);

    final Resource _resource;
    final Path _path;
    final String _contentType;
    final HttpField _etag;
    final ResourceService _resourceService;

    public ResourceHttpContent(Resource resource, String contentType, ResourceService resourceService)
    {
        _resource = resource;
        _resourceService = resourceService;
        _path = resource.getPath();
        _contentType = contentType;
        _etag = EtagUtils.createWeakEtagField(resource);
    }

    @Override
    public String getContentTypeValue()
    {
        return _contentType;
    }

    @Override
    public HttpField getContentType()
    {
        return _contentType == null ? null : new HttpField(HttpHeader.CONTENT_TYPE, _contentType);
    }

    @Override
    public HttpField getContentEncoding()
    {
        return null;
    }

    @Override
    public String getContentEncodingValue()
    {
        return null;
    }

    @Override
    public String getCharacterEncoding()
    {
        return _contentType == null ? null : MimeTypes.getCharsetFromContentType(_contentType);
    }

    @Override
    public Type getMimeType()
    {
        return _contentType == null ? null : MimeTypes.CACHE.get(MimeTypes.getContentTypeWithoutCharset(_contentType));
    }

    @Override
    public Instant getLastModifiedInstant()
    {
        return _resource.lastModified();
    }

    @Override
    public HttpField getLastModified()
    {
        Instant lm = _resource.lastModified();
        return new HttpField(HttpHeader.LAST_MODIFIED, DateGenerator.formatDate(lm));
    }

    @Override
    public String getLastModifiedValue()
    {
        Instant lm = _resource.lastModified();
        return DateGenerator.formatDate(lm);
    }

    @Override
    public HttpField getETag()
    {
        return _etag;
    }

    @Override
    public String getETagValue()
    {
        if (_etag == null)
            return null;
        return _etag.getValue();
    }

    @Override
    public HttpField getContentLength()
    {
        long l = getContentLengthValue();
        return l == -1 ? null : new HttpField.LongValueHttpField(HttpHeader.CONTENT_LENGTH, l);
    }

    @Override
    public long getContentLengthValue()
    {
        return _resource.length();
    }

    @Override
    public Resource getResource()
    {
        return _resource;
    }

    @Override
    public String toString()
    {
        return String.format("%s@%x{r=%s,ct=%s}", this.getClass().getSimpleName(), hashCode(), _resource, _contentType);
    }

    @Override
    public ByteBuffer getByteBuffer()
    {
        return null;
    }

    @Override
    public void release()
    {
    }

    @Override
    public void process(Request request, Response response, Callback callback) throws Exception
    {
        long contentLength = getContentLengthValue();

        if (LOG.isDebugEnabled())
            LOG.debug(String.format("sendData content=%s", this));

        // Is this a Range request?
        List<String> reqRanges = request.getHeaders().getValuesList(HttpHeader.RANGE.asString());
        if (reqRanges.isEmpty())
        {
            // If there are no ranges, send the entire content.
            if (contentLength >= 0)
                _resourceService.putHeaders(response, this, USE_KNOWN_CONTENT_LENGTH);
            else
                _resourceService.putHeaders(response, this, NO_CONTENT_LENGTH);
            new ContentWriterIteratingCallback(this, response, callback).iterate();
            return;
        }

        // Parse the satisfiable ranges.
        List<ByteRange> ranges = ByteRange.parse(reqRanges, contentLength);

        // If there are no satisfiable ranges, send a 416 response.
        if (ranges.isEmpty())
        {
            _resourceService.putHeaders(response, this, NO_CONTENT_LENGTH);
            response.getHeaders().put(HttpHeader.CONTENT_RANGE, ByteRange.toNonSatisfiableHeaderValue(contentLength));
            Response.writeError(request, response, callback, HttpStatus.RANGE_NOT_SATISFIABLE_416);
            return;
        }

        // If there is only a single valid range, send that range with a 206 response.
        if (ranges.size() == 1)
        {
            ByteRange range = ranges.get(0);
            _resourceService.putHeaders(response, this, range.getLength());
            response.setStatus(HttpStatus.PARTIAL_CONTENT_206);
            response.getHeaders().put(HttpHeader.CONTENT_RANGE, range.toHeaderValue(contentLength));
            Content.copy(new MultiPartByteRanges.PathContentSource(getResource().getPath(), range), response, callback);
            return;
        }

        // There are multiple non-overlapping ranges, send a multipart/byteranges 206 response.
        _resourceService.putHeaders(response, this, NO_CONTENT_LENGTH);
        response.setStatus(HttpStatus.PARTIAL_CONTENT_206);
        String contentType = "multipart/byteranges; boundary=";
        String boundary = MultiPart.generateBoundary(null, 24);
        response.getHeaders().put(HttpHeader.CONTENT_TYPE, contentType + boundary);
        MultiPartByteRanges.ContentSource byteRanges = new MultiPartByteRanges.ContentSource(boundary);
        ranges.forEach(range -> byteRanges.addPart(new MultiPartByteRanges.Part(getContentTypeValue(), getResource().getPath(), range, contentLength)));
        byteRanges.close();
        Content.copy(byteRanges, response, callback);
    }

    private static class ContentWriterIteratingCallback extends IteratingCallback
    {
        private final ReadableByteChannel source;
        private final Content.Sink sink;
        private final Callback callback;
        private final ByteBuffer byteBuffer;
        private final ByteBufferPool byteBufferPool;

        public ContentWriterIteratingCallback(HttpContent content, Response target, Callback callback) throws IOException
        {
            this.byteBufferPool = target.getRequest().getComponents().getByteBufferPool();
            this.source = content.getResource().newReadableByteChannel();
            this.sink = target;
            this.callback = callback;
            int outputBufferSize = target.getRequest().getConnectionMetaData().getHttpConfiguration().getOutputBufferSize();
            boolean useOutputDirectByteBuffers = target.getRequest().getConnectionMetaData().getHttpConfiguration().isUseOutputDirectByteBuffers();
            this.byteBuffer = byteBufferPool.acquire(outputBufferSize, useOutputDirectByteBuffers);
        }

        @Override
        protected Action process() throws Throwable
        {
            if (!source.isOpen())
                return Action.SUCCEEDED;

            BufferUtil.clearToFill(byteBuffer);
            int read = source.read(byteBuffer);
            if (read == -1)
            {
                IO.close(source);
                sink.write(true, BufferUtil.EMPTY_BUFFER, this);
                return Action.SCHEDULED;
            }
            BufferUtil.flipToFlush(byteBuffer, 0);
            sink.write(false, byteBuffer, this);
            return Action.SCHEDULED;
        }

        @Override
        protected void onCompleteSuccess()
        {
            byteBufferPool.release(byteBuffer);
            callback.succeeded();
        }

        @Override
        protected void onCompleteFailure(Throwable x)
        {
            byteBufferPool.release(byteBuffer);
            callback.failed(x);
        }
    }
}
