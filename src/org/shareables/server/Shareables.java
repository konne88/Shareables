/**
 * $$\\ToureNPlaner\\$$
 */
package org.shareables.server;

import org.apache.commons.pool.BasePoolableObjectFactory;
import org.apache.commons.pool.impl.GenericObjectPool;
import org.codehaus.jackson.JsonFactory;
import org.shareables.models.ListModel;
import org.shareables.models.MimeBlob;
import org.shareables.models.ModelFactory;
import org.shareables.models.ShareableModel;
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
        final JedisPool pool = new JedisPool(config, "localhost");
        final ScriptEngineManager manager = new ScriptEngineManager();

        GenericObjectPool<ScriptEngine> scriptEnginePool = new GenericObjectPool<ScriptEngine>(new BasePoolableObjectFactory<ScriptEngine>() {
            @Override
            public ScriptEngine makeObject() throws Exception {
                return manager.getEngineByExtension("js");
            }
        });
        scriptEnginePool.setConfig(config);
        ModelRegistry registry = new ModelRegistry();

        registry.addModel(new ModelFactory() {
            @Override
            public ShareableModel createModel() {
                return new MimeBlob(pool);
            }

            @Override
            public String getName() {
                return "mimeblob";
            }
        });

        final JsonFactory factory = new JsonFactory();

        registry.addModel(new ModelFactory() {
            @Override
            public ShareableModel createModel() {
                return new ListModel(pool, factory);
            }

            @Override
            public String getName() {
                return "list";
            }
        });


        new HttpServer(pool, scriptEnginePool, registry);
    }
}
