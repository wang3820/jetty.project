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
import java.util.List;

import org.eclipse.jetty.http.DateParser;
import org.eclipse.jetty.http.EtagUtils;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.http.PreEncodedHttpField;
import org.eclipse.jetty.http.QuotedCSV;
import org.eclipse.jetty.server.AliasCheck;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.URIUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resource service, used by DefaultServlet and ResourceHandler
 */
public class ResourceService
{
    private static final Logger LOG = LoggerFactory.getLogger(ResourceService.class);

    public static final int NO_CONTENT_LENGTH = -1;
    public static final int USE_KNOWN_CONTENT_LENGTH = -2;

    private boolean _etags = false;
    private List<String> _gzipEquivalentFileExtensions;
    private HttpContent.Factory _contentFactory;
    private boolean _dirAllowed = true;
    private boolean _acceptRanges = true;
    private HttpField _cacheControl;

    public ResourceService()
    {
    }

    public HttpContent getContent(String path, Request request) throws IOException
    {
        HttpContent content = _contentFactory.getContent(path == null ? "" : path);
        if (content != null)
        {
            AliasCheck aliasCheck = ContextHandler.getContextHandler(request);
            if (aliasCheck != null && !aliasCheck.checkAlias(path, content.getResource()))
                return null;
        }

        return content;
    }

    public HttpContent.Factory getHttpContentFactory()
    {
        return _contentFactory;
    }

    public void setHttpContentFactory(HttpContent.Factory contentFactory)
    {
        _contentFactory = contentFactory;
    }

    /**
     * @return the cacheControl header to set on all static content.
     */
    public String getCacheControl()
    {
        return _cacheControl.getValue();
    }

    /**
     * @return file extensions that signify that a file is gzip compressed. Eg ".svgz"
     */
    public List<String> getGzipEquivalentFileExtensions()
    {
        return _gzipEquivalentFileExtensions;
    }

    public void doGet(Request request, Response response, Callback callback, HttpContent content) throws Exception
    {
        String pathInContext = Request.getPathInContext(request);
        boolean endsWithSlash = pathInContext.endsWith("/");
        callback = Callback.from(callback, content::release);

        try
        {
            // Directory?
            if (content.getResource().isDirectory())
            {
                if (!endsWithSlash)
                {
                    // Redirect to directory
                    HttpURI.Mutable uri = HttpURI.build(request.getHttpURI());
                    if (!uri.getCanonicalPath().endsWith("/"))
                    {
                        uri.path(uri.getCanonicalPath() + "/");
                        response.getHeaders().putLongField(HttpHeader.CONTENT_LENGTH, 0);
                        sendRedirect(request, response, callback, uri.getPathQuery());
                        return;
                    }
                }
            }
            else
            {
                // Strip slash?
                if (endsWithSlash && pathInContext.length() > 1)
                {
                    // TODO need helper code to edit URIs
                    String q = request.getHttpURI().getQuery();
                    pathInContext = pathInContext.substring(0, pathInContext.length() - 1);
                    if (q != null && q.length() != 0)
                        pathInContext += "?" + q;
                    sendRedirect(request, response, callback, URIUtil.addPaths(request.getContext().getContextPath(), pathInContext));
                    return;
                }

                // Conditional response?
                if (passConditionalHeaders(request, response, content, callback))
                    return;

                if (isImplicitlyGzippedContent(pathInContext))
                    response.getHeaders().put(HttpHeader.CONTENT_ENCODING, "gzip");
            }

            // Send the data.
            sendData(request, response, callback, content);
        }
        catch (Throwable t)
        {
            LOG.warn("Failed to serve resource: {}", pathInContext, t);
            if (!response.isCommitted())
                writeHttpError(request, response, callback, t);
        }
    }

    public void writeHttpError(Request request, Response response, Callback callback, int status)
    {
        Response.writeError(request, response, callback, status);
    }

    public void writeHttpError(Request request, Response response, Callback callback, Throwable cause)
    {
        Response.writeError(request, response, callback, cause);
    }

    public void writeHttpError(Request request, Response response, Callback callback, int status, String msg, Throwable cause)
    {
        Response.writeError(request, response, callback, status, msg, cause);
    }

    public void sendRedirect(Request request, Response response, Callback callback, String target)
    {
        Response.sendRedirect(request, response, callback, target);
    }

    private boolean isImplicitlyGzippedContent(String path)
    {
        if (path == null || _gzipEquivalentFileExtensions == null)
            return false;

        for (String suffix : _gzipEquivalentFileExtensions)
        {
            if (path.endsWith(suffix))
                return true;
        }
        return false;
    }

    /**
     * @return true if the request was processed, false otherwise.
     */
    public boolean passConditionalHeaders(Request request, Response response, HttpContent content, Callback callback) throws IOException
    {
        try
        {
            String ifm = null;
            String ifnm = null;
            String ifms = null;
            String ifums = null;

            // Find multiple fields by iteration as an optimization
            for (HttpField field : request.getHeaders())
            {
                if (field.getHeader() != null)
                {
                    switch (field.getHeader())
                    {
                        case IF_MATCH -> ifm = field.getValue();
                        case IF_NONE_MATCH -> ifnm = field.getValue();
                        case IF_MODIFIED_SINCE -> ifms = field.getValue();
                        case IF_UNMODIFIED_SINCE -> ifums = field.getValue();
                        default ->
                        {
                        }
                    }
                }
            }

            if (_etags)
            {
                String etag = content.getETagValue();
                if (etag != null)
                {
                    // TODO: this is a hack to get the etag of the non-preCompressed version.
                    etag = EtagUtils.rewriteWithSuffix(content.getETagValue(), "");
                    if (ifm != null)
                    {
                        String matched = matchesEtag(etag, ifm);
                        if (matched == null)
                        {
                            writeHttpError(request, response, callback, HttpStatus.PRECONDITION_FAILED_412);
                            return true;
                        }
                    }

                    if (ifnm != null)
                    {
                        String matched = matchesEtag(etag, ifnm);
                        if (matched != null)
                        {
                            response.getHeaders().put(HttpHeader.ETAG, matched);
                            writeHttpError(request, response, callback, HttpStatus.NOT_MODIFIED_304);
                            return true;
                        }

                        // If etag requires content to be served, then do not check if-modified-since
                        return false;
                    }
                }
            }

            // Handle if modified since
            if (ifms != null && ifnm == null)
            {
                //Get jetty's Response impl
                String mdlm = content.getLastModifiedValue();
                if (ifms.equals(mdlm))
                {
                    writeHttpError(request, response, callback, HttpStatus.NOT_MODIFIED_304);
                    return true;
                }

                long ifmsl = DateParser.parseDate(ifms);
                if (ifmsl != -1)
                {
                    long lm = content.getResource().lastModified().toEpochMilli();
                    if (lm != -1 && lm / 1000 <= ifmsl / 1000)
                    {
                        writeHttpError(request, response, callback, HttpStatus.NOT_MODIFIED_304);
                        return true;
                    }
                }
            }

            // Parse the if[un]modified dates and compare to resource
            if (ifums != null && ifm == null)
            {
                long ifumsl = DateParser.parseDate(ifums);
                if (ifumsl != -1)
                {
                    long lm = content.getResource().lastModified().toEpochMilli();
                    if (lm != -1 && lm / 1000 > ifumsl / 1000)
                    {
                        writeHttpError(request, response, callback, HttpStatus.PRECONDITION_FAILED_412);
                        return true;
                    }
                }
            }
        }
        catch (IllegalArgumentException iae)
        {
            if (!response.isCommitted())
                writeHttpError(request, response, callback, HttpStatus.BAD_REQUEST_400, null, iae);
            throw iae;
        }

        return false;
    }

    /**
     * Find a matches between a Content ETag and a Request Field ETag reference.
     * @param contentETag the content etag to match against (can be null)
     * @param requestEtag the request etag (can be null, a single entry, or even a CSV list)
     * @return the matched etag, or null if no matches.
     */
    private String matchesEtag(String contentETag, String requestEtag)
    {
        if (contentETag == null || requestEtag == null)
        {
            return null;
        }

        // Per https://www.rfc-editor.org/rfc/rfc9110#section-8.8.3
        // An Etag header field value can contain a "," (comma) within itself.
        //   If-Match: W/"abc,xyz", "123456"
        // This means we have to parse with QuotedCSV all the time, as we cannot just
        // test for the existence of a "," (comma) in the value to know if it's delimited or not
        QuotedCSV quoted = new QuotedCSV(true, requestEtag);
        for (String tag : quoted)
        {
            if (EtagUtils.matches(contentETag, tag))
            {
                return tag;
            }
        }

        // no matches
        return null;
    }

    private void sendData(Request request, Response response, Callback callback, HttpContent content)
    {
        if (LOG.isDebugEnabled())
            LOG.debug(String.format("sendData content=%s", content));

        callback = Callback.from(callback, content::release);
        writeHttpContent(request, response, callback, content);
    }

    public void writeHttpContent(Request request, Response response, Callback callback, HttpContent content)
    {
        try
        {
            content.process(request, response, callback);
        }
        catch (Throwable x)
        {
            callback.failed(x);
        }
    }

    public void putHeaders(Response response, HttpContent content, long contentLength)
    {
        // TODO it is very inefficient to do many put's to a HttpFields, as each put is a full iteration.
        //      it might be better remove headers en masse and then just add the extras:
        // NOTE: If these headers come from a Servlet Filter we shouldn't override them here.
//        headers.remove(EnumSet.of(
//            HttpHeader.LAST_MODIFIED,
//            HttpHeader.CONTENT_LENGTH,
//            HttpHeader.CONTENT_TYPE,
//            HttpHeader.CONTENT_ENCODING,
//            HttpHeader.ETAG,
//            HttpHeader.ACCEPT_RANGES,
//            HttpHeader.CACHE_CONTROL
//            ));
//        HttpField lm = content.getLastModified();
//        if (lm != null)
//            headers.add(lm);
//        etc.

        HttpField lm = content.getLastModified();
        if (lm != null)
            response.getHeaders().put(lm);

        if (contentLength == USE_KNOWN_CONTENT_LENGTH)
        {
            response.getHeaders().put(content.getContentLength());
        }
        else if (contentLength > NO_CONTENT_LENGTH)
        {
            response.getHeaders().putLongField(HttpHeader.CONTENT_LENGTH, contentLength);
        }

        HttpField ct = content.getContentType();
        if (ct != null)
            response.getHeaders().put(ct);

        HttpField ce = content.getContentEncoding();
        if (ce != null)
            response.getHeaders().put(ce);

        if (_etags)
        {
            HttpField et = content.getETag();
            if (et != null)
                response.getHeaders().put(et);
        }

        if (_acceptRanges && !response.getHeaders().contains(HttpHeader.ACCEPT_RANGES))
            response.getHeaders().put(new PreEncodedHttpField(HttpHeader.ACCEPT_RANGES, "bytes"));
        if (_cacheControl != null && !response.getHeaders().contains(HttpHeader.CACHE_CONTROL))
            response.getHeaders().put(_cacheControl);
    }

    /**
     * @return If true, range requests and responses are supported
     */
    public boolean isAcceptRanges()
    {
        return _acceptRanges;
    }

    /**
     * @return If true, directory listings are returned if no welcome file is found. Else 403 Forbidden.
     */
    public boolean isDirAllowed()
    {
        return _dirAllowed;
    }

    /**
     * @return True if ETag processing is done
     */
    public boolean isEtags()
    {
        return _etags;
    }

    /**
     * @param acceptRanges If true, range requests and responses are supported
     */
    public void setAcceptRanges(boolean acceptRanges)
    {
        _acceptRanges = acceptRanges;
    }

    /**
     * @param cacheControl the cacheControl header to set on all static content.
     */
    public void setCacheControl(String cacheControl)
    {
        _cacheControl = new PreEncodedHttpField(HttpHeader.CACHE_CONTROL, cacheControl);
    }

    /**
     * @param dirAllowed If true, directory listings are returned if no welcome file is found. Else 403 Forbidden.
     */
    public void setDirAllowed(boolean dirAllowed)
    {
        _dirAllowed = dirAllowed;
    }

    /**
     * @param etags True if ETag processing is done
     */
    public void setEtags(boolean etags)
    {
        _etags = etags;
    }

    /**
     * @param gzipEquivalentFileExtensions file extensions that signify that a file is gzip compressed. Eg ".svgz"
     */
    public void setGzipEquivalentFileExtensions(List<String> gzipEquivalentFileExtensions)
    {
        _gzipEquivalentFileExtensions = gzipEquivalentFileExtensions;
    }
}
