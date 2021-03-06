/**
 * $$\\ToureNPlaner\\$$
 */
package org.shareables.server;

import org.apache.commons.pool.impl.GenericObjectPool;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import redis.clients.jedis.JedisPool;

import javax.script.ScriptEngine;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

// TODO: maybe move more of this to the main TourenPlaner

/**
 * Shareables Event Based Server
 * 
 * This is the main class used to start the server
 * 
 * @author Niklas Schnelle
 * @version 0.1 Prototype
 * 
 *          Initially based on:
 *          http://docs.jboss.org/netty/3.2/xref/org/jboss/netty
 *          /example/http/snoop/package-summary.html
 */
public class HttpServer {

    public HttpServer(JedisPool pool, GenericObjectPool<ScriptEngine> jsPool, ModelRegistry registry) {
        // Configure the server.

		ServerBootstrap bootstrap = new ServerBootstrap(
				new NioServerSocketChannelFactory( // Change to Oio* if you want
													// OIO
						Executors.newCachedThreadPool(), Executors
								.newCachedThreadPool()));

			// Set up the event pipeline factory without ssl
			bootstrap.setPipelineFactory(new ServerPipelineFactory(pool, jsPool, registry));
			// Bind and start to accept incoming connections.
			bootstrap.bind(new InetSocketAddress(8080));
	}
}
