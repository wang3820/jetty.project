package org.eclipse.jetty.nested.api;

import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.util.Callback;

public interface NestedResponse extends Content.Sink, Callback
{
    void setStatus(int status);

    void addHeader(String name, String value);
}
