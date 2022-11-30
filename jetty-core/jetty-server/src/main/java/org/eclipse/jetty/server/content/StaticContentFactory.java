package org.eclipse.jetty.server.content;

import java.io.IOException;

import org.eclipse.jetty.server.ResourceService;
import org.eclipse.jetty.util.resource.Resource;

public class StaticContentFactory implements HttpContent.Factory
{
    private final HttpContent.Factory _factory;
    private final ResourceService _resourceService;
    private Resource _styleSheet;

    public StaticContentFactory(HttpContent.Factory factory, ResourceService resourceService)
    {
        _factory = factory;
        _resourceService = resourceService;
    }

    public StaticContentFactory(HttpContent.Factory factory, ResourceService resourceService, Resource styleSheet)
    {
        _factory = factory;
        _resourceService = resourceService;
        _styleSheet = styleSheet;
    }

    /**
     * @param stylesheet The location of the stylesheet to be used as a String.
     */
    public void setStyleSheet(Resource stylesheet)
    {
        _styleSheet = stylesheet;
    }

    /**
     * @return Returns the stylesheet as a Resource.
     */
    public Resource getStyleSheet()
    {
        return _styleSheet;
    }


    @Override
    public HttpContent getContent(String path) throws IOException
    {
        HttpContent content = _factory.getContent(path);
        if (content != null)
            return content;

        if ((_styleSheet != null) && (path != null) && path.endsWith("/jetty-dir.css"))
            return new ResourceHttpContent(_styleSheet, "text/css", _resourceService);

        return null;
    }
}
