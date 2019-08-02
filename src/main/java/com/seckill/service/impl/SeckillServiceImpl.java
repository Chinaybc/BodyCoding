package com.seckill.service.impl;

import com.seckill.dao.SeckillDao;
import com.seckill.dao.SuccessKilledDao;
import com.seckill.dao.cache.RedisDao;
import com.seckill.dto.Exposer;
import com.seckill.dto.SeckillExecution;
import com.seckill.entity.Seckill;
import com.seckill.entity.SuccessKilled;
import com.seckill.enums.SeckillStateEnum;
import com.seckill.exception.RepeatKillException;
import com.seckill.exception.SeckillCloseException;
import com.seckill.exception.SeckillException;
import com.seckill.service.SeckillService;
import org.apache.commons.collections.MapUtils;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import java.util.*;
import java.util.concurrent.*;

@Service
public class SeckillServiceImpl implements SeckillService {

    private static final Log LOG = LogFactory.getLog(SeckillServiceImpl.class);

    private final static int TIMEOUT = 5 * 1000;

    private static CountDownLatch countDownLatch = new CountDownLatch(4);

    @Autowired
    private SeckillDao seckillDao;

    @Autowired
    private RedisDao redisDao;

    @Autowired
    private SuccessKilledDao successKilledDao;

    private String slat = "skjhdasfinin$@$#fdjofjdsg0AEsisjd0";

    public List<Seckill> getSeckillList() {
        return seckillDao.queryAll(0,4);
    }

    public Seckill getById(long seckillId) {
        return seckillDao.queryById(seckillId);
    }

    public Exposer exportSeckillUrl(long seckillId) {

        //缓存优化
        Seckill seckill = redisDao.getSeckill(seckillId);
        if (seckill == null) {
            seckill = seckillDao.queryById(seckillId);
            if (seckill == null){
                return new Exposer(false,seckillId);
            }else {
                redisDao.putSeckill(seckill);
            }
        }
        Date startTime = seckill.getStartTime();
        Date endTime = seckill.getEndTime();
        Date cur = new Date();
        if (cur.getTime() > endTime.getTime() || cur.getTime() < startTime.getTime()){
            return new Exposer(false,seckillId,cur.getTime(),startTime.getTime(),endTime.getTime());
        }
        String md5 = getMD5(seckillId);
        return new Exposer(true,md5,seckillId);
    }

    private String getMD5(long seckillId){
        String base =  seckillId + "/" + slat;
        String md5 = DigestUtils.md5DigestAsHex(base.getBytes());
        return md5;
    }

    @Transactional
    public SeckillExecution executeSeckill(long seckillId, long userPhone, String md5) throws SeckillException, RepeatKillException, SeckillCloseException {
        if (md5 == null || !md5.equals(getMD5(seckillId))){
            throw new SeckillException("seckill data rewrite");
        }
        Date now = new Date();
        try {
            int insertCount = successKilledDao.insertSuccessKilled(seckillId, userPhone);
            if (insertCount <= 0) {
                throw new RepeatKillException("seckill is repeated");
            } else {
                int updateCount = seckillDao.reduceNumber(seckillId, now);
                if (updateCount <= 0) {
                    throw new SeckillCloseException("seckill is closed");
                } else {
                    SuccessKilled successKilled = successKilledDao.queryByIdWithSeckill(seckillId, userPhone);
                    return new SeckillExecution(seckillId, SeckillStateEnum.SUCCESS, successKilled);
                }
            }
        }catch (SeckillCloseException e){
            throw  e;
        }catch (RepeatKillException e){
            throw e;
        }catch (Exception e){
            LOG.error(e.getMessage(),e);
            throw new SeckillException("seckill inner error" + e.getMessage());
        }
    }

    public SeckillExecution executeSeckillProcedure(long seckillId, long userPhone, String md5)
            throws SeckillException, RepeatKillException, SeckillCloseException {
        if (md5 == null || !md5.equals(getMD5(seckillId)))
            throw new SeckillException("seckill data rewrite");
        Date date = new Date();
        Map<String,Object> map = new HashMap<String, Object>();
        map.put("seckillId",seckillId);
        map.put("phone",userPhone);
        map.put("killTime",date);
        map.put("result",null);
        try {
            seckillDao.killByProcedure(map);
            int result = MapUtils.getInteger(map,"result",-2);
            if (result == 1){
                SuccessKilled sk = successKilledDao.queryByIdWithSeckill(seckillId,userPhone);
                return new SeckillExecution(seckillId,SeckillStateEnum.SUCCESS,sk);
            }else {
                return new SeckillExecution(seckillId,SeckillStateEnum.stateOf(result));
            }
        }catch (Exception e){
            LOG.error(e.getMessage(),e);
            return new SeckillExecution(seckillId,SeckillStateEnum.INNER_ERROR);
        }
    }

    public SeckillExecution executeByRedis(long seckillId, long userPhone, String md5) throws SeckillException, RepeatKillException, SeckillCloseException {
        if (md5 == null || !md5.equals(getMD5(seckillId)))
            throw new SeckillException("seckill data rewrite");
        try {
            Set<String> set = redisDao.getSuccesskill(seckillId);
            //判断是否重复购买
            if (set.contains(seckillId + "," + userPhone))
                return new SeckillExecution(seckillId, SeckillStateEnum.REPEAT_KILL);

            long time = System.currentTimeMillis() + TIMEOUT;
            boolean lock = redisDao.lock(String.valueOf(seckillId), String.valueOf(time));
            if (!lock) {
                return new SeckillExecution(seckillId, SeckillStateEnum.WAIT_FAIL);
            }
            int stockNum = redisDao.getStock("stock:" + String.valueOf(seckillId));
            if (stockNum == Integer.MIN_VALUE) {
                Seckill seckill = seckillDao.queryById(seckillId);
                stockNum = seckill.getNumber();
                redisDao.pushStock(seckillId, String.valueOf(seckill.getNumber()));
            }
            if (stockNum <= 0)
                return new SeckillExecution(seckillId, SeckillStateEnum.FAIL);
            redisDao.pushStock(seckillId, String.valueOf(stockNum - 1));
            SuccessKilled successKilled = new SuccessKilled(seckillId, userPhone);
            redisDao.putSuccesskill(successKilled);
            int count = redisDao.getSuccesskillSize(seckillId);
            if (count % 500 == 0) {
                set = redisDao.getSuccesskill(seckillId);
                final List<String> list = new ArrayList<String>(set);
                ThreadPoolExecutor executor;
                executor = new ThreadPoolExecutor(4, 4, 0L, TimeUnit.MICROSECONDS, new ArrayBlockingQueue<Runnable>(0));
                executor.execute(new Runnable() {
                    public void run() {
                        for (int i = 0; i < list.size() / 4; i++) {
                            String[] info = list.get(i).split(",");
                            successKilledDao.insertSuccessKilled(Long.parseLong(info[0]), Long.parseLong(info[1]));
                        }
                        countDownLatch.countDown();
                    }
                });

                executor.execute(new Runnable() {
                    public void run() {
                        for (int i = list.size() / 4; i < list.size() / 2; i++) {
                            String[] info = list.get(i).split(",");
                            successKilledDao.insertSuccessKilled(Long.parseLong(info[0]), Long.parseLong(info[1]));
                        }
                        countDownLatch.countDown();
                    }
                });

                executor.execute(new Runnable() {
                    public void run() {
                        for (int i = list.size() / 2; i < list.size() * 3 / 4; i++) {
                            String[] info = list.get(i).split(",");
                            successKilledDao.insertSuccessKilled(Long.parseLong(info[0]), Long.parseLong(info[1]));
                        }
                        countDownLatch.countDown();
                    }
                });

                executor.execute(new Runnable() {
                    public void run() {
                        for (int i = list.size() * 3 / 4; i < list.size(); i++) {
                            String[] info = list.get(i).split(",");
                            successKilledDao.insertSuccessKilled(Long.parseLong(info[0]), Long.parseLong(info[1]));
                        }
                        countDownLatch.countDown();
                    }
                });
                try {
                    countDownLatch.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }catch (Exception e){
            LOG.error(e.getMessage(),e);
        }
        return new SeckillExecution(seckillId,SeckillStateEnum.SUCCESS);
    }
}
