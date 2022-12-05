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
import java.nio.charset.StandardCharsets;

import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.ResourceListing;
import org.eclipse.jetty.server.ResourceService;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.URIUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WelcomeHttpContentFactory implements HttpContent.Factory
{
    private static final Logger LOG = LoggerFactory.getLogger(WelcomeHttpContentFactory.class);

    private final ResourceService _resourceService;
    private final HttpContent.Factory _factory;
    private WelcomeFactory _welcomeFactory;
    private boolean _redirectWelcome = false;
    private boolean _dirAllowed = true;
    private boolean _etags = false;

    public interface WelcomeFactory
    {
        /**
         * Finds a matching welcome target URI path for the request.
         *
         * @param request the request to use to determine the matching welcome target from.
         * @return The URI path of the matching welcome target in context or null
         * (null means no welcome target was found)
         */
        String getWelcomeTarget(Request request) throws IOException;
    }

    public enum WelcomeActionType
    {
        REDIRECT,
        SERVE
    }
    
    /**
     * Behavior for a potential welcome action
     * as determined by {@link #processWelcome(Request, Response)}
     *
     * <p>
     * For {@link WelcomeActionType#REDIRECT} this is the resulting `Location` response header.
     * For {@link WelcomeActionType#SERVE} this is the resulting path to for welcome serve, note that
     * this is just a path, and can point to a real file, or a dynamic handler for
     * welcome processing (such as Jetty core Handler, or EE Servlet), it's up
     * to the implementation of {@link #welcome(Request, Response, Callback)}
     * to handle the various action types.
     * </p>
     *
     * @param type the type of action
     * @param target The target URI path of the action.
     */
    public record WelcomeAction(WelcomeActionType type, String target) {}

    public WelcomeHttpContentFactory(HttpContent.Factory factory, ResourceService resourceService)
    {
        this(factory, resourceService, null, false);
    }

    public WelcomeHttpContentFactory(HttpContent.Factory factory, ResourceService resourceService, WelcomeFactory welcomeFactory, boolean redirectWelcome)
    {
        _factory = factory;
        _resourceService = resourceService;
        _welcomeFactory = welcomeFactory;
        _redirectWelcome = redirectWelcome;
    }

    public WelcomeFactory getWelcomeFactory()
    {
        return _welcomeFactory;
    }

    public void setWelcomeFactory(WelcomeFactory welcomeFactory)
    {
        _welcomeFactory = welcomeFactory;
    }

    /**
     * @param redirectWelcome If true, welcome files are redirected rather than forwarded to.
     * redirection is always used if the ResourceHandler is not scoped by
     * a ContextHandler
     */
    public void setRedirectWelcome(boolean redirectWelcome)
    {
        _redirectWelcome = redirectWelcome;
    }

    /**
     * @return If true, welcome files are redirected rather than forwarded to.
     */
    public boolean isRedirectWelcome()
    {
        return _redirectWelcome;
    }

    /**
     * @param dirAllowed If true, directory listings are returned if no welcome file is found. Else 403 Forbidden.
     */
    public void setDirAllowed(boolean dirAllowed)
    {
        _dirAllowed = dirAllowed;
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
     * @param etags True if ETag processing is done
     */
    public void setEtags(boolean etags)
    {
        _etags = etags;
    }

    @Override
    public HttpContent getContent(String path) throws IOException
    {
        HttpContent content = _factory.getContent(path);
        if (content != null && content.getResource().isDirectory())
            return new WelcomeHttpContent(content);
        return content;
    }

    private class WelcomeHttpContent extends HttpContent.Wrapper
    {
        public WelcomeHttpContent(HttpContent content)
        {
            super(content);
        }

        @Override
        public void process(Request request, Response response, Callback callback) throws Exception
        {
            sendWelcome(this, request, response, callback);
        }
    }

    protected void sendWelcome(HttpContent content, Request request, Response response, Callback callback) throws Exception
    {
        // process optional Welcome behaviors
        if (welcome(request, response, callback))
            return;

        if (!_resourceService.passConditionalHeaders(request, response, content, callback))
            sendDirectory(content, request, response, callback);
    }

    private boolean welcome(Request request, Response response, Callback callback) throws IOException
    {
        WelcomeAction welcomeAction = processWelcome(request, response);
        if (welcomeAction == null)
            return false;

        welcomeActionProcess(request, response, callback, welcomeAction);
        return true;
    }

    private WelcomeAction processWelcome(Request request, Response response) throws IOException
    {
        String welcomeTarget = _welcomeFactory.getWelcomeTarget(request);
        if (welcomeTarget == null)
            return null;

        String contextPath = request.getContext().getContextPath();

        if (LOG.isDebugEnabled())
            LOG.debug("welcome={}", welcomeTarget);

        if (_redirectWelcome)
        {
            // Redirect to the index
            response.getHeaders().putLongField(HttpHeader.CONTENT_LENGTH, 0);
            HttpURI.Mutable uri = HttpURI.build(request.getHttpURI());
            uri.path(URIUtil.addPaths(contextPath, welcomeTarget));
            return new WelcomeAction(WelcomeActionType.REDIRECT, uri.getPathQuery());
        }

        // Serve welcome file
        return new WelcomeAction(WelcomeActionType.SERVE, welcomeTarget);
    }

    private void sendDirectory(HttpContent httpContent, Request request, Response response, Callback callback)
    {
        if (!_dirAllowed)
        {
            _resourceService.writeHttpError(request, response, callback, HttpStatus.FORBIDDEN_403);
            return;
        }

        String pathInContext = Request.getPathInContext(request);
        String base = URIUtil.addEncodedPaths(request.getHttpURI().getPath(), "/");
        String listing = ResourceListing.getAsXHTML(httpContent.getResource(), base, pathInContext.length() > 1, request.getHttpURI().getQuery());
        if (listing == null)
        {
            _resourceService.writeHttpError(request, response, callback, HttpStatus.FORBIDDEN_403);
            return;
        }

        byte[] data = listing.getBytes(StandardCharsets.UTF_8);
        response.getHeaders().put(HttpHeader.CONTENT_TYPE, "text/html;charset=utf-8");
        response.getHeaders().putLongField(HttpHeader.CONTENT_LENGTH, data.length);
        response.write(true, ByteBuffer.wrap(data), callback);
    }

    protected void welcomeActionProcess(Request request, Response response, Callback callback, WelcomeAction welcomeAction) throws IOException
    {
        switch (welcomeAction.type())
        {
            case REDIRECT ->
            {
                response.getHeaders().putLongField(HttpHeader.CONTENT_LENGTH, 0);
                _resourceService.sendRedirect(request, response, callback, welcomeAction.target());
            }
            case SERVE ->
            {
                // TODO : check conditional headers.
                HttpContent content = _factory.getContent(welcomeAction.target());
                _resourceService.writeHttpContent(request, response, callback, content);
            }
        }
    }
}
