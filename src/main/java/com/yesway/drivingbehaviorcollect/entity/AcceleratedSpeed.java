package com.yesway.drivingbehaviorcollect.entity;

import java.util.Date;

public class AcceleratedSpeed {
    private float xAsixSpeed;
    private float yAsixSpeed;
    private float zAsixSpeed;
    private double averageSpeed;
    private long timestamp;
    private Date currTime;
    private String calculateResult;

    public float getxAsixSpeed() {
        return xAsixSpeed;
    }

    public void setxAsixSpeed(float xAsixSpeed) {
        this.xAsixSpeed = xAsixSpeed;
    }

    public float getyAsixSpeed() {
        return yAsixSpeed;
    }

    public void setyAsixSpeed(float yAsixSpeed) {
        this.yAsixSpeed = yAsixSpeed;
    }

    public float getzAsixSpeed() {
        return zAsixSpeed;
    }

    public void setzAsixSpeed(float zAsixSpeed) {
        this.zAsixSpeed = zAsixSpeed;
    }

    public double getAverageSpeed() {
        return averageSpeed;
    }

    public void setAverageSpeed(double averageSpeed) {
        this.averageSpeed = averageSpeed;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public Date getCurrTime() {
        return currTime;
    }

    public void setCurrTime(Date currTime) {
        this.currTime = currTime;
    }

    public String getCalculateResult() {
        return calculateResult;
    }

    public void setCalculateResult(String calculateResult) {
        this.calculateResult = calculateResult;
    }

    @Override
    public String toString() {
        return xAsixSpeed + "," + yAsixSpeed + "," + zAsixSpeed + "," + averageSpeed + "," + timestamp + "," + currTime + "," + calculateResult;
    }

}
