/**
 * $$\\ToureNPlaner\\$$
 */

package org.shareables.server;

import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.*;
import org.shareables.models.ShareableModel;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static org.jboss.netty.handler.codec.http.HttpHeaders.isKeepAlive;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * This handler handles HTTP Requests on the normal operation socket including *
 *
 * @author Niklas Schnelle, Konstantin Weitz
 * @version 0.1
 *          <p/>
 *          Initially based on: http://docs.jboss.org/netty/3.2/xref
 *          /org/jboss/netty/example/http/snoop/package-summary.html
 */
public class MasterHandler extends SimpleChannelUpstreamHandler {
    private static Logger log = Logger.getLogger("org.shareable.server");
    private Responder responder;
    private final JedisPool jedisPool;
    private final ModelRegistry registry;
    /**
     * Constructs a new RequestHandler
     * @param pool
     */
    public MasterHandler(JedisPool pool, ModelRegistry registry) {
        this.jedisPool = pool;
        this.registry = registry;
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        responder = new Responder(e.getChannel());
    }


    /**
     * Called when a message is received
     */
    @Override
    public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent e) throws Exception {
        final HttpRequest request = (HttpRequest) e.getMessage();
        final Channel channel = e.getChannel();
        // System.out.print(request.toString());
        // Handle preflighted requests so wee need to work with OPTION Requests
        if (request.getMethod().equals(HttpMethod.OPTIONS)) {
            handlePreflights(request, channel);
            return;
        }

        // Get the Requeststring e.g. /info
        final QueryStringDecoder queryStringDecoder = new QueryStringDecoder(request.getUri());
        final String path = queryStringDecoder.getPath();

        log.info("Request for: " + path);
        //log.finer("Request: " + request.getContent().toString(CharsetUtil.UTF_8));
        request.getContent().readerIndex(0);
        responder.setKeepAlive(isKeepAlive(request));

        if (path.startsWith("/new/")){
            handleNew(request, path);
        } else if (path.startsWith("/use/")){
            handleUse(request, path);
        } else if (path.startsWith("/update/")){
            handleUpdate(request, path);
        } else if (path.startsWith("/lastupdated/")){
            handleLastUpdated(request, path);
        } else {
            responder.writeErrorMessage("EUNKNONURL", "The url "+path+" is unknown", "", HttpResponseStatus.OK);
        }

    }

    private void handleUpdate(HttpRequest request, String path) throws Exception {
        final int endOfUseIndex = "/update/".length();
        final int slashIndex = path.indexOf('/', endOfUseIndex + 1);
        final int endOfKeyIndex = (slashIndex > 0) ? slashIndex : path.length();

        String key = path.substring(endOfUseIndex, endOfKeyIndex);
        Jedis jedis = jedisPool.getResource();
        try {
            // Get model name
            String modelName = jedis.hget("meta:" + key, "model");
            ShareableModel model = registry.getModel(modelName);
            if (model != null) {
                boolean  success = model.updateObject(request, key, request.getUri().substring(endOfKeyIndex));
                if (success){
                    jedis.hset("meta:"+key, "last-updated", Long.toString(System.currentTimeMillis()));
                    responder.writeStatusResponse(HttpResponseStatus.OK);
                } else {
                    responder.writeErrorMessage("EBADUPDATE", "Could not update, might be concurrent access", "", HttpResponseStatus.BAD_REQUEST);
                }
            } else {
                responder.writeErrorMessage("EBAUPDATE", "Could not find " + key, "", HttpResponseStatus.NOT_FOUND);
            }
        } finally {
            jedisPool.returnResource(jedis);
        }
    }

    private void handleUse(HttpRequest request, String path) throws Exception {
        final int endOfUseIndex = "/use/".length();
        final int slashIndex = path.indexOf('/', endOfUseIndex + 1);
        final int endOfKeyIndex = (slashIndex > 0)?slashIndex:path.length();

        String key = path.substring(endOfUseIndex, endOfKeyIndex);
        Jedis jedis = jedisPool.getResource();
        try {
            // Get model name
            String modelName = jedis.hget("meta:"+key, "model");
            ShareableModel model = registry.getModel(modelName);
            if (model != null) {
                model.useObject(request, key, request.getUri().substring(endOfKeyIndex), responder);
            } else {
                responder.writeErrorMessage("EBADUSE", "Could not find " + key, "", HttpResponseStatus.NOT_FOUND);
            }

        } finally {
            jedisPool.returnResource(jedis);
        }
    }

    private void handleNew(HttpRequest request, String path) throws Exception {
        final int endOfNewIndex = "/new/".length();
        String modelName = path.substring(endOfNewIndex);
        ShareableModel model = registry.getModel(modelName);
        if(model != null){
            String key = UUID.randomUUID().toString();
            Jedis jedis = jedisPool.getResource();
            try {
                // Check if key existis
                long success = jedis.hsetnx("meta:" + key, "model", modelName);
                if(success == 1){
                    model.newObject(request, key);
                    jedis.hset("meta:" + key, "last-updated", Long.toString(System.currentTimeMillis()));
                    responder.writeString("shrbl://"+key, "application/x-shareable-key", HttpResponseStatus.OK);
                } else {
                    responder.writeErrorMessage("EBADNEW", "The key "+key+" already exists can't do new", "", HttpResponseStatus.BAD_REQUEST);
                }
            } finally {
                jedisPool.returnResource(jedis);
            }

        } else {
            responder.writeErrorMessage("EUNKNOWNMODEL", "The model "+modelName+" is unknown to this server", "", HttpResponseStatus.NOT_FOUND);
        }
    }

    private void handleLastUpdated(HttpRequest request, String path) throws IOException {
        final int endOfLastUpdated = "/lastupdated/".length();
        final int slashIndex = path.indexOf('/', endOfLastUpdated + 1);
        final int endOfKeyIndex = (slashIndex > 0) ? slashIndex : path.length();

        String key = path.substring(endOfLastUpdated, endOfKeyIndex);
        Jedis jedis = jedisPool.getResource();
        try {
            // Get model name
            String lastUpdated = jedis.hget("meta:" + key, "last-updated");
            if (lastUpdated != null) {
                responder.writeString(lastUpdated, "application/x-shareable-timestamp", HttpResponseStatus.OK);
            } else {
                responder.writeErrorMessage("EBADLASTUPDATED", "Could not find " + key, "", HttpResponseStatus.NOT_FOUND);
            }

        } finally {
            jedisPool.returnResource(jedis);
        }
    }

    /**
     * Handles preflighted OPTION Headers
     *
     * @param request HttpRequest
     * @param channel Channel
     */
    private void handlePreflights(final HttpRequest request, final Channel channel) {
        boolean keepAlive = isKeepAlive(request);
        HttpResponse response;

        // We only allow POST and GET methods so only allow request when Method
        // is Post or Get
        final String methodType = request.getHeader("Access-Control-Request-Method");
        if ((methodType != null) && (methodType.trim().equals("POST") || methodType.trim().equals("GET"))) {
            response = new DefaultHttpResponse(HTTP_1_1, OK);
            response.addHeader("Connection", "Keep-Alive");
        } else {
            response = new DefaultHttpResponse(HTTP_1_1, FORBIDDEN);
            // We don't want to keep the connection now
            keepAlive = false;
        }

        final ArrayList<String> allowHeaders = new ArrayList<String>(2);
        allowHeaders.add("Content-Type");
        allowHeaders.add("Authorization");

        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
        response.setHeader(CONTENT_TYPE, "application/json");
        response.setHeader("Content-Length", "0");

        response.setHeader("Access-Control-Allow-Headers", allowHeaders);

        final ChannelFuture future = channel.write(response);
        if (!keepAlive) {
            future.addListener(ChannelFutureListener.CLOSE);
        }
    }

    /**
     * Called when an uncaught exception occurs
     */
    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final ExceptionEvent e) throws Exception {
        // Ignore if it's just a client cutting the connection
        if(!(e.getCause() instanceof IOException)){
            log.log(Level.WARNING, "Exception caught", e.getCause());
        }
        e.getChannel().close();
    }
}
