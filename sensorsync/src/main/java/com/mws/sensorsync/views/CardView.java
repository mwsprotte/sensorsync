package com.mws.sensorsync.views;

import java.util.Objects;

public class CardView {

    String sensorDescription, dataDesc, readTime;

    double data;

    long sensorIndex;

    public CardView() {
    }

    public String getSensorDescription() {
        return sensorDescription;
    }

    public void setSensorDescription(String sensorDescription) {
        this.sensorDescription = sensorDescription;
    }

    public String getDataDesc() {
        return dataDesc;
    }

    public void setDataDesc(String dataDesc) {
        this.dataDesc = dataDesc;
    }

    public String getReadTime() {
        return readTime;
    }

    public void setReadTime(String readTime) {
        this.readTime = readTime;
    }

    public double getData() {
        return data;
    }

    public void setData(double data) {
        this.data = data;
    }

    public long getSensorIndex() {
        return sensorIndex;
    }

    public void setSensorIndex(long sensorIndex) {
        this.sensorIndex = sensorIndex;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CardView cardView = (CardView) o;
        return Double.compare(data, cardView.data) == 0 && sensorIndex == cardView.sensorIndex && Objects.equals(sensorDescription, cardView.sensorDescription) && Objects.equals(dataDesc, cardView.dataDesc) && Objects.equals(readTime, cardView.readTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sensorDescription, dataDesc, readTime, data, sensorIndex);
    }
}
