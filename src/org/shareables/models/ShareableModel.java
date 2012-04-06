package org.shareables.models;

import org.jboss.netty.handler.codec.http.HttpRequest;
import org.shareables.server.Responder;

/**
 * @author Niklas Schnelle
 * TODO: Add mechanism to deal with chunked requests
 *
 * Classes implementing this interface are used to access shareables using their model
 * they need to be thread save so shouldn't hold state outside of methods
 */
public interface ShareableModel {

    public String getName();
    public void newObject(HttpRequest request, String key) throws Exception;
    public void useObject(HttpRequest request, String key, String callUri, Responder responder) throws  Exception;
    public boolean updateObject(HttpRequest request, String key, String callUri) throws Exception;

}
