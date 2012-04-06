package org.shareables.models;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.util.CharsetUtil;
import org.shareables.server.Responder;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Transaction;

import java.util.List;

/**
 * @author Niklas Schnelle
 */
public class MimeBlob extends RedisBackedModel{

    public MimeBlob(JedisPool jedisPool) {
        super(jedisPool);
    }

    @Override
    public String getName() {
        return "mimeblob";
    }

    @Override
    public void newObject(HttpRequest request, String key) throws Exception{
        String storeKey = "store:"+key;
        String mimetype = request.getHeader("Content-Type");
        if(mimetype == null || mimetype.equals(""))
            mimetype = "unknown";

        ChannelBuffer data = request.getContent();
        Jedis jedis = jedisPool.getResource();
        Pipeline pipe = jedis.pipelined();
        try {
            pipe.hset(storeKey, "mime-type", mimetype);
            pipe.hset(storeKey.getBytes(CharsetUtil.US_ASCII), "value".getBytes(CharsetUtil.US_ASCII), data.array());
            pipe.sync();
        } finally {
            jedisPool.returnResource(jedis);
        }

    }

    @Override
    public void useObject(HttpRequest request, String key, String callUri, Responder responder) throws Exception{
        String storeKey = "store:" + key;
        Jedis jedis = jedisPool.getResource();
        byte[] value;
        String mimeType;
        try {
            mimeType = jedis.hget(storeKey, "mime-type");
            value = jedis.hget(storeKey.getBytes(CharsetUtil.US_ASCII), "value".getBytes(CharsetUtil.US_ASCII));
        } finally {
            jedisPool.returnResource(jedis);
        }
        responder.writeByteArray(value, mimeType, HttpResponseStatus.OK);
    }

    @Override
    public boolean updateObject(HttpRequest request, String key, String callUri) throws Exception {
        String storeKey = "store:" + key;
        String mimetype = request.getHeader("Content-Type");
        if (mimetype == null || mimetype.equals(""))
            mimetype="unknown";

        ChannelBuffer data = request.getContent();
        List<Object> reply;
        Jedis jedis = jedisPool.getResource();
        try {
            jedis.watch(storeKey);
            Transaction trans = jedis.multi();
            trans.hset(storeKey, "mime-type", mimetype);
            trans.hset(storeKey.getBytes(CharsetUtil.US_ASCII), "value".getBytes(CharsetUtil.US_ASCII), data.array());
            reply = trans.exec();
        } finally {
            jedisPool.returnResource(jedis);
        }
        return reply != null;
    }
}
