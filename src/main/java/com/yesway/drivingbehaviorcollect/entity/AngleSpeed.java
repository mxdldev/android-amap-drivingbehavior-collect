package com.yesway.drivingbehaviorcollect.entity;

import java.util.Date;

public class AngleSpeed {
    private float xAsixSpeed;
    private float yAsixSpeed;
    private float zAsixSpeed;
    private double angleSpeed;
    private float xRadian;
    private float yRadian;
    private float zRadian;
    private double averageRadian;
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

    public double getAngleSpeed() {
        return angleSpeed;
    }

    public void setAngleSpeed(double angleSpeed) {
        this.angleSpeed = angleSpeed;
    }

    public float getxRadian() {
        return xRadian;
    }

    public void setxRadian(float xRadian) {
        this.xRadian = xRadian;
    }

    public float getyRadian() {
        return yRadian;
    }

    public void setyRadian(float yRadian) {
        this.yRadian = yRadian;
    }

    public float getzRadian() {
        return zRadian;
    }

    public void setzRadian(float zRadian) {
        this.zRadian = zRadian;
    }

    public double getAverageRadian() {
        return averageRadian;
    }

    public void setAverageRadian(double averageRadian) {
        this.averageRadian = averageRadian;
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
        return xAsixSpeed + "," + yAsixSpeed + "," + zAsixSpeed + "," + angleSpeed + "," + xRadian + "," + yRadian + "," + zRadian + "," + averageRadian + "," + timestamp + "," + currTime + "," + calculateResult;
    }

}
