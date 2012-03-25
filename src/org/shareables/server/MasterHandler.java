/**
 * $$\\ToureNPlaner\\$$
 */

package org.shareables.server;

import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.*;
import org.jboss.netty.util.CharsetUtil;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Pipeline;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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
    private ScriptEngineManager manager;
    private ScriptEngine js;
    private JedisPool pool;
    /**
     * Constructs a new RequestHandler
     * @param pool
     */
    public MasterHandler(JedisPool pool) {
        this.pool = pool;
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        responder = new Responder(e.getChannel());
	    manager = new ScriptEngineManager();
	    js = manager.getEngineByExtension("js");
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
        log.finer("Request: " + request.getContent().toString(CharsetUtil.UTF_8));
        request.getContent().readerIndex(0);

        responder.setKeepAlive(isKeepAlive(request));

        String[] components = path.split("/");
        
        if (components[1].equals("new") && request.getMethod().equals(HttpMethod.POST)) {
        	// /new/$model/$lang
            if(components.length >= 4){
                String model = components[2];
                String lang = components[3];

                byte[] data = request.getContent().toByteBuffer().array();

                String mimetype = request.getHeader("Content-Type");
                // Add language execution here

                String key = model+":"+UUID.randomUUID().toString();
                log.info("model: " + model + ", lang: " + lang + ", key: " + key);

                // create new object with the following id model and language
                Jedis jedi = pool.getResource();
                try {
                    Pipeline pipe = jedi.pipelined();
                    pipe.hset(key, "model", model);
                    pipe.hset(key, "lang", lang);
                    if ("mimeblob".equals(model)) {
                        pipe.hset(key, "mime-type", mimetype);
                    }
                    pipe.hset(key.getBytes(CharsetUtil.US_ASCII), "value".getBytes(CharsetUtil.US_ASCII), data);
                    pipe.sync();
                } finally {
                    pool.returnResource(jedi);
                }

                // Write a response
                responder.writeString(key, "application/x-shareable-key", HttpResponseStatus.OK);
            } else {
                responder.writeErrorMessage("EBADNEW", "New neeeds to be /new/$model/$lang","", HttpResponseStatus.BAD_REQUEST);
            }




        }
        else if(components[1].equals("update")){
        	// /update/$id
        	String id = components[2];
        	// store script in object with this id
        } 
        else if(components[1].equals("get")){
        	// /get/$id
            Jedis jedi = pool.getResource();
            String key = components[2];
            String contentType;
            String model;
            String lang;
            byte[] value;
	        try {
                List<String> meta = jedi.hmget(key, "model", "lang", "mime-type");
                model = meta.get(0);
                lang = meta.get(1);
                if("mimeblob".equals(model)){
                    contentType = meta.get(2);
                } else {
                    contentType = "application/json";
                }
	        	value = jedi.hget(key.getBytes(CharsetUtil.US_ASCII), "value".getBytes(CharsetUtil.US_ASCII)); // get js from db with this id
	        } finally {
                pool.returnResource(jedi);
            }
            /*Object json = js.eval(objScript);
            responder.writeJSON(json, HttpResponseStatus.OK);*/
            responder.writeByteArray(value, contentType, HttpResponseStatus.OK);
        }
        else {
            // Unknown request, close connection
            log.warning("An unknown URL was requested: " + path);
            responder.writeErrorMessage("EUNKNOWNURL", "An unknown URL was requested", "unknown URL: " + path, HttpResponseStatus.NOT_FOUND);
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
