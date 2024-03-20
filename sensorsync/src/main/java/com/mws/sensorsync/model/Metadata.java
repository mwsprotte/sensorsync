package com.mws.sensorsync.model;

import jakarta.persistence.*;

import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "metadata")
public class Metadata implements Serializable {

    private static final long serialVersionUID = 1L;


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column
    private long projectID;


    @Column
    private long dataIndex;

    @Column(length = 254)
    private String dataDesc;

    @Column
    private boolean useCard;

    @Column
    private boolean useChart;

    public Metadata() {
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getProjectID() {
        return projectID;
    }

    public void setProjectID(long projectID) {
        this.projectID = projectID;
    }

    public long getDataIndex() {
        return dataIndex;
    }

    public void setDataIndex(long dataIndex) {
        this.dataIndex = dataIndex;
    }

    public String getDataDesc() {
        return dataDesc;
    }

    public void setDataDesc(String dataDesc) {
        this.dataDesc = dataDesc;
    }

    public boolean isUseCard() {
        return useCard;
    }

    public void setUseCard(boolean useCard) {
        this.useCard = useCard;
    }

    public boolean isUseChart() {
        return useChart;
    }

    public void setUseChart(boolean useChart) {
        this.useChart = useChart;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Metadata metadata = (Metadata) o;
        return id == metadata.id && projectID == metadata.projectID && dataIndex == metadata.dataIndex && useCard == metadata.useCard && useChart == metadata.useChart && Objects.equals(dataDesc, metadata.dataDesc);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, projectID, dataIndex, dataDesc, useCard, useChart);
    }
}
