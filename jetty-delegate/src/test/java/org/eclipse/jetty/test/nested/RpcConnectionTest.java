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

package org.eclipse.jetty.test.nested;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Enumeration;
import javax.servlet.AsyncContext;
import javax.servlet.DispatcherType;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.delegate.DelegateConnector;
import org.eclipse.jetty.ee8.servlet.ServletContextHandler;
import org.eclipse.jetty.ee8.servlet.ServletHolder;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.test.nested.impl.DelegateRpcExchange;
import org.eclipse.jetty.test.nested.rpc.MockRpcRequest;
import org.eclipse.jetty.test.nested.rpc.MockRpcResponse;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.IO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RpcConnectionTest
{
    Server server;
    DelegateConnector connector;

    @BeforeEach
    public void before() throws Exception
    {
        server = new Server();
        connector = new DelegateConnector(server, "RPC");
        server.addConnector(connector);

        ServletContextHandler servletContextHandler = new ServletContextHandler();
        servletContextHandler.addServlet(new ServletHolder(new HttpServlet()
        {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
            {
                // We can do async request dispatches.
                if (req.getDispatcherType() == DispatcherType.REQUEST)
                {
                    AsyncContext asyncContext = req.startAsync();
                    asyncContext.dispatch();
                    return;
                }

                // Setting custom headers.
                resp.setHeader("customHeader", "1234556");
                resp.setContentType("text/plain");

                // Get headers from the RPC request.
                PrintWriter writer = resp.getWriter();
                writer.println("request headers: " + getRequestHeadersAsString(req));

                // Read content from the RPC request.
                writer.println("request content: " + IO.toString(req.getInputStream()));
                writer.println();

                // Get things like async attributes.
                writer.println("request attribute ASYNC_MAPPING: " + req.getAttribute(AsyncContext.ASYNC_MAPPING));
                writer.println("request attribute ASYNC_QUERY_STRING: " + req.getAttribute(AsyncContext.ASYNC_QUERY_STRING));
                writer.println("request attribute ASYNC_SERVLET_PATH: " + req.getAttribute(AsyncContext.ASYNC_SERVLET_PATH));
                writer.println("request attribute ASYNC_CONTEXT_PATH: " + req.getAttribute(AsyncContext.ASYNC_CONTEXT_PATH));
                writer.println("request attribute ASYNC_PATH_INFO: " + req.getAttribute(AsyncContext.ASYNC_PATH_INFO));
                writer.println("request attribute ASYNC_REQUEST_URI: " + req.getAttribute(AsyncContext.ASYNC_REQUEST_URI));
            }
        }), "/");
        server.setHandler(servletContextHandler);

        server.start();
    }

    @AfterEach
    public void after() throws Exception
    {
        server.stop();
    }

    private static String getRequestHeadersAsString(HttpServletRequest req)
    {
        StringBuilder headers = new StringBuilder();
        headers.append("{");
        Enumeration<String> headerNames = req.getHeaderNames();
        while (headerNames.hasMoreElements())
        {
            String element = headerNames.nextElement();
            headers.append(element).append(": ").append(req.getHeader(element));
            if (headerNames.hasMoreElements())
                headers.append(",");
        }
        headers.append("}");
        return headers.toString();
    }

    @Test
    public void test() throws Exception
    {
        HttpFields.Mutable add = HttpFields.build().add("Host", "localhost");
        MockRpcRequest request = new MockRpcRequest("GET", "/hello", "HTTP/1.1", add, BufferUtil.toBuffer("test input"));
        MockRpcResponse response = new MockRpcResponse();

        DelegateRpcExchange delegateExchange = new DelegateRpcExchange(request, response);
        connector.service(delegateExchange);
        delegateExchange.awaitResponse();

        System.err.println("Response Status: " + response.getStatusCode());
        for (HttpField field : response.getFields())
        {
            System.err.println("    " + field.getName() + ": " + field.getValue());
        }
        System.err.println("\nResponse Content: ");
        System.err.println("============================================");
        System.err.println(BufferUtil.toString(response.getContent()));
        System.err.println("============================================");
    }
}
