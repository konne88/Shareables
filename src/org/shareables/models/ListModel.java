package org.shareables.models;

import org.codehaus.jackson.JsonFactory;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.QueryStringDecoder;
import org.shareables.org.shareables.utils.ShareableUrlMatcher;
import org.shareables.server.Responder;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Pipeline;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *@author  Niklas Schnelle
 */
public class ListModel extends  RedisBackedModel{

    private final JsonFactory fac;

    public ListModel(JedisPool jedisPool, JsonFactory fac) {
        super(jedisPool);
        this.fac = fac;
    }

    @Override
    public String getName() {
        return "list";
    }



    @Override
    public void newObject(HttpRequest request, String key) throws Exception {}

    @Override
    public void useObject(HttpRequest request, String key, String callUri, Responder responder) throws Exception {
        String storeKey = "store:"+key;
        QueryStringDecoder decoder = new QueryStringDecoder(callUri);
        String path = decoder.getPath();
        Jedis jedis = jedisPool.getResource();
        try {
            if(path.startsWith("/getat")){
                Map<String, List<String>> params = decoder.getParameters();
                List<String> indexList = params.get("i");
                if(indexList != null && indexList.size() > 0){
                    long index = Long.valueOf(indexList.get(0));
                    String valueKey = jedis.lindex(storeKey, index);
                    responder.writeString(valueKey, "application/x-shareable-key", HttpResponseStatus.OK);
                } else {
                    responder.writeErrorMessage("ELISTINDEX", "List index not found ", "", HttpResponseStatus.NOT_FOUND);
                }

            } else if (path.startsWith("/json")){
                List<String> list = jedis.lrange(storeKey,0,-1);
                Map<String, Object> map = new HashMap<String, Object>();
                map.put("list", list);
                responder.writeJSON(map, HttpResponseStatus.OK);
            } else if (path.startsWith("/getlength")){
                long length = jedis.llen(storeKey);
                responder.writeString(Long.toString(length),"application/x-shareable-long", HttpResponseStatus.OK);
            } else {
                responder.writeErrorMessage("ELISTOP", "Unknown list operation "+path, "", HttpResponseStatus.NOT_FOUND);
            }
        } catch (NumberFormatException numf){
            responder.writeErrorMessage("ELISTINDEX", "List index needs to be a number", "", HttpResponseStatus.BAD_REQUEST);
        } finally {
            jedisPool.returnResource(jedis);
        }


    }

    @Override
    public boolean updateObject(HttpRequest request, String key, String callUri) throws Exception {
        String storeKey = "store:" + key;
        QueryStringDecoder decoder = new QueryStringDecoder(callUri);
        String path = decoder.getPath();
        boolean success = false;
        Jedis jedis = jedisPool.getResource();
        try {
            if (path.startsWith("/append")) {
                Map<String, List<String>> params = decoder.getParameters();
                List<String> keyList = params.get("key");
                if (keyList != null && keyList.size() > 0) {
                    Pipeline pipe = jedis.pipelined();
                    success = true;
                    for(String currKey : keyList){
                        success = success && ShareableUrlMatcher.isShareableKey(currKey);

                        pipe.rpush(storeKey, "shrbl://"+currKey);
                    }
                    if(success)
                        pipe.sync();
                }

            }
        } finally {
            jedisPool.returnResource(jedis);
        }
        return success;
    }


}
