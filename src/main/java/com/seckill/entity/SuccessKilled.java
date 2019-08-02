package com.seckill.entity;

import java.util.Date;

public class SuccessKilled {

    private long secKillId;

    private long userPhone;

    private short state;

    private Date createTime;

    private Seckill seckill;

    public SuccessKilled(long secKillId, long userPhone, short state, Date createTime, Seckill seckill) {
        this.secKillId = secKillId;
        this.userPhone = userPhone;
        this.state = state;
        this.createTime = createTime;
        this.seckill = seckill;
    }

    public SuccessKilled(long secKillId, long userPhone) {
        this.secKillId = secKillId;
        this.userPhone = userPhone;
    }

    public long getSecKillId() {
        return secKillId;
    }

    public void setSecKillId(long secKillId) {
        this.secKillId = secKillId;
    }

    public long getUserPhone() {
        return userPhone;
    }

    public void setUserPhone(long userPhone) {
        this.userPhone = userPhone;
    }


    public short getState() {
        return state;
    }

    public void setState(short state) {
        this.state = state;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Seckill getSeckill() {
        return seckill;
    }

    public void setSeckill(Seckill seckill) {
        this.seckill = seckill;
    }
}
