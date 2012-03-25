/**
 * $$\\ToureNPlaner\\$$
 */
package org.shareables.server;

import org.shareables.server.HttpServer;

import java.util.logging.Logger;

public class Shareables {

    private static Logger log = Logger.getLogger("org.shareables");


    /**
     * This is the main class of ToureNPlaner. It passes CLI parameters to the
     * handler and creates the httpserver
     */
    public static void main(String[] args) {
        new HttpServer();
    }

}
