package com.mws.sensorsync.model;

import jakarta.persistence.*;

import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "data")
public class Data implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(length = 254)
    private String sensorDescription;

    @Column
    private long projectID;

    @Column
    private long sensorIndex;

    @Column
    private long dataIndex;

    @Column
    private double data;

    @Column
    private String readTime;

    public Data() {
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getSensorDescription() {
        return sensorDescription;
    }

    public void setSensorDescription(String sensorDescription) {
        this.sensorDescription = sensorDescription;
    }

    public long getProjectID() {
        return projectID;
    }

    public void setProjectID(long projectID) {
        this.projectID = projectID;
    }

    public long getSensorIndex() {
        return sensorIndex;
    }

    public void setSensorIndex(long sensorIndex) {
        this.sensorIndex = sensorIndex;
    }

    public long getDataIndex() {
        return dataIndex;
    }

    public void setDataIndex(long dataIndex) {
        this.dataIndex = dataIndex;
    }

    public double getData() {
        return data;
    }

    public void setData(double data) {
        this.data = data;
    }

    public String getReadTime() {
        return readTime;
    }

    public void setReadTime(String readTime) {
        this.readTime = readTime;
    }
}
