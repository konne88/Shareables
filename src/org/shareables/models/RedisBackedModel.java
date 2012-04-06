package org.shareables.models;

import redis.clients.jedis.JedisPool;

/**
 * @author Niklas Schnelle
 */
public abstract class RedisBackedModel implements ShareableModel{
    protected JedisPool jedisPool;

    public RedisBackedModel(JedisPool jedisPool){
        this.jedisPool = jedisPool;
    }
}
