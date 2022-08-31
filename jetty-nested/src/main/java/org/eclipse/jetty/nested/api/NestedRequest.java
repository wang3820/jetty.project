package org.eclipse.jetty.nested.api;

import java.net.InetSocketAddress;

import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.io.Content;

public interface NestedRequest extends Content.Source
{
    String getRequestURI();

    String getProtocol();

    String getMethod();

    HttpFields getHeaders();

    InetSocketAddress getRemoteAddr();

    InetSocketAddress getLocalAddr();
}
