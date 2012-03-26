/**
 * $$\\ToureNPlaner\\$$
 */
package org.shareables.server;

import org.apache.commons.pool.BasePoolableObjectFactory;
import org.apache.commons.pool.impl.GenericObjectPool;
import redis.clients.jedis.JedisPool;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.util.logging.Logger;

public class Shareables {

    private static Logger log = Logger.getLogger("org.shareables");


    /**
     * This is the main class of ToureNPlaner. It passes CLI parameters to the
     * handler and creates the httpserver
     */
    public static void main(String[] args) {
        GenericObjectPool.Config config = new GenericObjectPool.Config();
        config.whenExhaustedAction = GenericObjectPool.WHEN_EXHAUSTED_GROW;
        config.maxIdle = 32;
        config.maxActive = 48;
        JedisPool pool = new JedisPool(config, "localhost");
        final ScriptEngineManager manager = new ScriptEngineManager();

        GenericObjectPool<ScriptEngine> scriptEnginePool = new GenericObjectPool<ScriptEngine>(new BasePoolableObjectFactory<ScriptEngine>() {
            @Override
            public ScriptEngine makeObject() throws Exception {
                return manager.getEngineByExtension("js");
            }
        });
        scriptEnginePool.setConfig(config);
        new HttpServer(pool, scriptEnginePool);
    }
}
