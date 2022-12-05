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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jetty.http.CompressedContentFormat;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.QuotedQualityCSV;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.ResourceService;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.content.HttpContent.Factory;
import org.eclipse.jetty.util.Callback;

public class PreCompressedHttpContentFactory implements HttpContent.Factory
{
    private final HttpContent.Factory _factory;
    private final List<CompressedContentFormat> _preCompressedFormats;
    private final List<String> _preferredEncodingOrder;
    private final Map<String, List<String>> _preferredEncodingOrderCache = new ConcurrentHashMap<>();
    private final ResourceService _resourceService;
    private int _encodingCacheSize;

    public PreCompressedHttpContentFactory(Factory factory, ResourceService resourceService, CompressedContentFormat[] preCompressedFormats)
    {
        this(factory, resourceService, Arrays.asList(preCompressedFormats));
    }

    public PreCompressedHttpContentFactory(HttpContent.Factory factory, ResourceService resourceService, List<CompressedContentFormat> preCompressedFormats)
    {
        this(factory, resourceService, preCompressedFormats, 100);
    }

    public PreCompressedHttpContentFactory(HttpContent.Factory factory, ResourceService resourceService, List<CompressedContentFormat> preCompressedFormats, int encodingCacheSize)
    {
        this(factory, resourceService, preCompressedFormats, encodingCacheSize, Collections.emptyList());
    }

    public PreCompressedHttpContentFactory(HttpContent.Factory factory, ResourceService resourceService, List<CompressedContentFormat> preCompressedFormats, int encodingCacheSize, List<String> preferredEncodingOrder)
    {
        _factory = factory;
        _preCompressedFormats = preCompressedFormats;
        _encodingCacheSize = encodingCacheSize;
        _preferredEncodingOrder = preferredEncodingOrder;
        _resourceService = resourceService;
    }

    /**
     * @return Precompressed resources formats that can be used to serve compressed variant of resources.
     */
    public List<CompressedContentFormat> getPrecompressedFormats()
    {
        return _preCompressedFormats;
    }

    /**
     * @param precompressedFormats The list of precompresed formats to serve in encoded format if matching resource found.
     * For example serve gzip encoded file if ".gz" suffixed resource is found.
     */
    public void setPrecompressedFormats(List<CompressedContentFormat> precompressedFormats)
    {
        _preCompressedFormats.clear();
        _preCompressedFormats.addAll(precompressedFormats);
        // TODO: this preferred encoding order should be a separate configurable
        _preferredEncodingOrder.clear();
        _preferredEncodingOrder.addAll(_preCompressedFormats.stream().map(CompressedContentFormat::getEncoding).toList());
    }

    public void setEncodingCacheSize(int encodingCacheSize)
    {
        _encodingCacheSize = encodingCacheSize;
        if (encodingCacheSize > _preferredEncodingOrderCache.size())
            _preferredEncodingOrderCache.clear();
    }

    public int getEncodingCacheSize()
    {
        return _encodingCacheSize;
    }

    @Override
    public HttpContent getContent(String pathInContext) throws IOException
    {
        HttpContent content = _factory.getContent(pathInContext);
        if (content == null)
            return null;

        Map<CompressedContentFormat, HttpContent> compressedFormats = new HashMap<>();
        for (CompressedContentFormat contentFormat : _preCompressedFormats)
        {
            // TODO: Alias check for pre-compressed content?
            HttpContent preCompressedContent = _factory.getContent(pathInContext + contentFormat.getExtension());
            if (preCompressedContent != null)
                compressedFormats.put(contentFormat, new PreCompressedHttpContent(content, preCompressedContent, contentFormat));
        }

        if (compressedFormats.isEmpty())
            return content;
        return new CompressedFormatsHttpContent(content, compressedFormats);
    }

    @Override
    public String toString()
    {
        return "%s@%x[%s,%s]".formatted(getClass().getSimpleName(), hashCode(), _factory, _preCompressedFormats);
    }

    private class CompressedFormatsHttpContent extends HttpContent.Wrapper
    {
        private final Map<CompressedContentFormat, HttpContent> compressedFormats;

        public CompressedFormatsHttpContent(HttpContent content, Map<CompressedContentFormat, HttpContent> compressedFormats)
        {
            super(content);
            this.compressedFormats = compressedFormats;
        }

        @Override
        public void process(Request request, Response response, Callback callback) throws Exception
        {
            // Response content may vary depending on Accept-Encoding header.
            response.getHeaders().add(HttpHeader.VARY, HttpHeader.ACCEPT_ENCODING.asString());

            // If we have an available compressed format we can serve that HttpContent.
            Collection<CompressedContentFormat> compressedContentFormats = compressedFormats.keySet();
            if (!compressedContentFormats.isEmpty())
            {
                List<String> preferredEncodingOrder = getPreferredEncodingOrder(request);
                if (!preferredEncodingOrder.isEmpty())
                {
                    for (String encoding : preferredEncodingOrder)
                    {
                        CompressedContentFormat contentFormat = isEncodingAvailable(encoding, compressedContentFormats);
                        if (contentFormat == null)
                            continue;


                        HttpContent preCompressedContent = new PreCompressedHttpContent(this, compressedFormats.get(contentFormat), contentFormat);

                        // Intercept response.write() to add headers modified by the pre-compressed content.
                        response = new Response.Wrapper(request, response)
                        {
                            @Override
                            public void write(boolean last, ByteBuffer byteBuffer, Callback callback)
                            {
                                if (!isCommitted())
                                {
                                    getHeaders().put(preCompressedContent.getContentType());
                                    getHeaders().put(preCompressedContent.getContentEncoding());
                                    getHeaders().put(preCompressedContent.getETag());
                                }

                                super.write(last, byteBuffer, callback);
                            }
                        };

                        preCompressedContent.process(request, response, callback);
                        return;
                    }
                }
            }

            // Otherwise serve the original content.
            response.getHeaders().put(getContentEncoding());
            super.process(request, response, callback);
        }

        private List<String> getPreferredEncodingOrder(Request request)
        {
            Enumeration<String> headers = request.getHeaders().getValues(HttpHeader.ACCEPT_ENCODING.asString());
            if (!headers.hasMoreElements())
                return Collections.emptyList();

            String key = headers.nextElement();
            if (headers.hasMoreElements())
            {
                StringBuilder sb = new StringBuilder(key.length() * 2);
                do
                {
                    sb.append(',').append(headers.nextElement());
                }
                while (headers.hasMoreElements());
                key = sb.toString();
            }

            List<String> values = _preferredEncodingOrderCache.get(key);
            if (values == null)
            {
                QuotedQualityCSV encodingQualityCSV = new QuotedQualityCSV(_preferredEncodingOrder);
                encodingQualityCSV.addValue(key);
                values = encodingQualityCSV.getValues();

                // keep cache size in check even if we get strange/malicious input
                if (_preferredEncodingOrderCache.size() > _encodingCacheSize)
                    _preferredEncodingOrderCache.clear();

                _preferredEncodingOrderCache.put(key, values);
            }

            return values;
        }

        private CompressedContentFormat isEncodingAvailable(String encoding, Collection<CompressedContentFormat> availableFormats)
        {
            if (availableFormats.isEmpty())
                return null;

            for (CompressedContentFormat format : availableFormats)
            {
                if (format.getEncoding().equals(encoding))
                    return format;
            }

            if ("*".equals(encoding))
                return availableFormats.iterator().next();
            return null;
        }
    }
}
