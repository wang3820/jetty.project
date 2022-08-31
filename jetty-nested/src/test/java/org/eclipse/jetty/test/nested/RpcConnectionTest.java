package org.eclipse.jetty.test.nested;

import java.io.IOException;
import java.util.Enumeration;
import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.ee8.servlet.ServletContextHandler;
import org.eclipse.jetty.ee8.servlet.ServletHolder;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.nested.NestedConnector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.test.nested.impl.NestedRpcRequest;
import org.eclipse.jetty.test.nested.impl.NestedRpcResponse;
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
    NestedConnector connector;

    @BeforeEach
    public void before() throws Exception
    {
        server = new Server();
        connector = new NestedConnector(server);
        server.addConnector(connector);

        ServletContextHandler servletContextHandler = new ServletContextHandler();
        servletContextHandler.addServlet(new ServletHolder(new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
            {
                AsyncContext asyncContext = req.startAsync();
                asyncContext.start(() ->
                {
                    try
                    {
                        String requestContent = IO.toString(req.getInputStream());

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

                        resp.getWriter().println("requset headers: " + headers.toString());
                        resp.getWriter().println("request content: " + requestContent);

                        asyncContext.complete();
                    }
                    catch (Throwable t)
                    {
                        t.printStackTrace();
                    }
                });
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

    @Test
    public void test() throws Exception
    {
        HttpFields.Mutable add = HttpFields.build().add("Host", "localhost");
        MockRpcRequest request = new MockRpcRequest("GET", "/hello", "HTTP/1.1", add, BufferUtil.toBuffer("test input"));
        MockRpcResponse response = new MockRpcResponse();

        NestedRpcRequest nestedRpcRequest = new NestedRpcRequest(request);
        NestedRpcResponse nestedRpcResponse = new NestedRpcResponse(response);
        connector.service(nestedRpcRequest, nestedRpcResponse);

        nestedRpcResponse.awaitResponse();
        System.err.println("Response Status: " + response.getStatusCode());
        for (HttpField field : response.getFields())
        {
            System.err.println("    " + field.getName() + ": " + field.getValue());
        }
        System.err.println("Response Content: ");
        System.err.println(BufferUtil.toString(response.getContent()));
    }
}
