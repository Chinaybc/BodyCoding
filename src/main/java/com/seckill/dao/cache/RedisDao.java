package com.seckill.dao.cache;

import com.alibaba.druid.util.StringUtils;
import com.dyuproject.protostuff.LinkedBuffer;
import com.dyuproject.protostuff.ProtostuffIOUtil;
import com.dyuproject.protostuff.runtime.RuntimeSchema;
import com.seckill.entity.Seckill;
import com.seckill.entity.SuccessKilled;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.Set;

public class RedisDao {

    private static final Log LOG = LogFactory.getLog(RedisDao.class);

    private final JedisPool jedisPool;

    private RuntimeSchema<Seckill> schema = RuntimeSchema.createFrom(Seckill.class);

    private RuntimeSchema<SuccessKilled> successKilledRuntimeSchema = RuntimeSchema.createFrom(SuccessKilled.class);

    public RedisDao(String ip,int port){
        jedisPool = new JedisPool(ip,port);
    }

    public Seckill getSeckill(long seckillId){
        try {
            Jedis jedis = jedisPool.getResource();
            try {
                String key = "com/seckill" + seckillId;
                byte[] bytes = jedis.get(key.getBytes());
                if (bytes != null){
                    Seckill seckill = schema.newMessage();
                    ProtostuffIOUtil.mergeFrom(bytes,seckill,schema);
                    return seckill;
                }
            }finally {
                jedis.close();
            }
        }catch (Exception e){
            LOG.error(e.getMessage(),e);
        }
        return null;
    }

    public String putSeckill(Seckill seckill){
        try {
            Jedis jedis = jedisPool.getResource();
            try {
                String key = "seckill:" + seckill.getSeckillId();
                byte[] bytes = ProtostuffIOUtil.toByteArray(seckill,schema,
                        LinkedBuffer.allocate(LinkedBuffer.DEFAULT_BUFFER_SIZE));
                int timeout = 60 * 60;
                String result = jedis.setex(key.getBytes(),timeout,bytes);
                return result;
            }finally {
                jedis.close();
            }
        }catch (Exception e){
            LOG.error(e.getMessage(),e);
        }
        return null;
    }

    public boolean lock(String seckillId,String value){
        try {
            Jedis jedis= jedisPool.getResource();
            try {
                String key = "seckillId:" + seckillId;
                if (jedis.setnx(key,value) != 0)
                    return true;
                String cur = jedis.get(key);
                if(!StringUtils.isEmpty(cur) && Long.parseLong(cur) < System.currentTimeMillis()){
                    String oldValue =  jedis.getSet(key,value);
                    if (!StringUtils.isEmpty(oldValue) && oldValue == cur)
                        return true;
                }
            }finally {
                jedis.close();
            }
        }catch (Exception e){
            LOG.error(e.getMessage(),e);
        }
        return false;
    }

    public void unlock(String key,String value){
        try {
            Jedis jedis = jedisPool.getResource();
            try {
                String oldValue = jedis.get(key);
                if (!StringUtils.isEmpty(oldValue) && oldValue.equals(value)){
                    jedis.del(key);
                }
            }finally {
                jedis.close();
            }
        }catch (Exception e){
            LOG.error(e.getMessage(),e);
        }
    }

    public int getStock(String key){
        try{
            Jedis jedis = jedisPool.getResource();
            try {
                String stockNum = jedis.get(key);
                if (stockNum != null)
                    return Integer.getInteger(stockNum);
            }finally {
                jedis.close();
            }
        }catch (Exception e){
            LOG.error(e.getMessage(),e);
        }finally {
        }
        return Integer.MIN_VALUE;
    }

    public void pushStock(long seckillId,String stockNum){
        try {
            Jedis jedis = jedisPool.getResource();
            try {
                String key = "stock:" + String.valueOf(seckillId);
                jedis.set(key,stockNum);
            }finally {
                jedis.close();
            }
        }catch (Exception e){
            LOG.error(e.getMessage(),e);
        }
    }

    public long putSuccesskill(SuccessKilled successKilled){
        try {
            Jedis jedis = jedisPool.getResource();
            try {
                String key = "successKilled:" + successKilled.getSecKillId();
                String value = String.valueOf(successKilled.getSeckill()) + "," + successKilled.getUserPhone();
                long result = jedis.sadd(key,value);
                return result;
            }finally {
                jedis.close();
            }
        }catch (Exception e){
            LOG.error(e.getMessage(),e);
        }
        return 0L;
    }

    public Set<String> getSuccesskill(long seckillId){
        try {
            Jedis jedis = jedisPool.getResource();
            try {
                String key = "successKilled:" + seckillId;
                Set<String> set = jedis.smembers(key);
                return set;
            }finally {
                jedis.close();
            }
        }catch (Exception e){
            LOG.error(e.getMessage(),e);
        }
        return null;
    }

    public int getSuccesskillSize(long seckillId){
        try {
            Jedis jedis = jedisPool.getResource();
            try {
                String key = "successKilled:" + seckillId;
                Set<String> set = jedis.smembers(key);
                return set.size();
            }finally {
                jedis.close();
            }
        }catch (Exception e){
            LOG.error(e.getMessage(),e);
        }
        return 0;
    }

}
