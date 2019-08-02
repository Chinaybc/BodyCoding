package com.seckill.dto;

public class SeckillResult<T> {

    private boolean success;

    private T date;

    private String error;

    public SeckillResult(boolean success, String error) {
        this.success = success;
        this.error = error;
    }

    public SeckillResult(boolean success, T date) {
        this.success = success;
        this.date = date;
    }

    public SeckillResult(boolean success, T date, String error) {
        this.success = success;
        this.date = date;
        this.error = error;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public T getDate() {
        return date;
    }

    public void setDate(T date) {
        this.date = date;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
