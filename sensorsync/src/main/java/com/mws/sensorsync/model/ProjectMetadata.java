package com.mws.sensorsync.model;

import jakarta.persistence.*;

import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "projectmetadata")
public class ProjectMetadata implements Serializable {

    private static final long serialVersionUID = 1L;


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(length = 254)
    private String projectName;

    @Column
    private long nodeNumber;

    @Column
    private long dataNumber;

    @Column(length = 254)
    private String data0Desc;

    @Column(length = 254)
    private String data1Desc;

    @Column(length = 254)
    private String data2Desc;

    @Column(length = 254)
    private String data3Desc;

    @Column(length = 254)
    private String data4Desc;

    @Column(length = 254)
    private String data5Desc;

    @Column(length = 254)
    private String data6Desc;

    @Column(length = 254)
    private String data7Desc;

    @Column(length = 254)
    private String data8Desc;

    @Column(length = 254)
    private String data9Desc;

    @Column(length = 254)
    private String data10Desc;

    @Column(length = 254)
    private String data11Desc;

    @Column(length = 254)
    private String data12Desc;

    @Column(length = 254)
    private String data13Desc;

    @Column(length = 254)
    private String data14Desc;

    @Column(length = 254)
    private String data15Desc;

    @Column(length = 254)
    private String data16Desc;

    @Column(length = 254)
    private String data17Desc;

    @Column(length = 254)
    private String data18Desc;

    @Column(length = 254)
    private String data19Desc;

    @Column
    private boolean data0UseCard;

    @Column
    private boolean data1UseCard;

    @Column
    private boolean data2UseCard;

    @Column
    private boolean data3UseCard;

    @Column
    private boolean data4UseCard;

    @Column
    private boolean data5UseCard;

    @Column
    private boolean data6UseCard;

    @Column
    private boolean data7UseCard;

    @Column
    private boolean data8UseCard;

    @Column
    private boolean data9UseCard;

    @Column
    private boolean data10UseCard;

    @Column
    private boolean data11UseCard;

    @Column
    private boolean data12UseCard;

    @Column
    private boolean data13UseCard;

    @Column
    private boolean data14UseCard;

    @Column
    private boolean data15UseCard;

    @Column
    private boolean data16UseCard;

    @Column
    private boolean data17UseCard;

    @Column
    private boolean data18UseCard;

    @Column
    private boolean data19UseCard;


    @Column
    private boolean data0UseChart;

    @Column
    private boolean data1UseChart;

    @Column
    private boolean data2UseChart;

    @Column
    private boolean data3UseChart;

    @Column
    private boolean data4UseChart;

    @Column
    private boolean data5UseChart;

    @Column
    private boolean data6UseChart;

    @Column
    private boolean data7UseChart;

    @Column
    private boolean data8UseChart;

    @Column
    private boolean data9UseChart;

    @Column
    private boolean data10UseChart;

    @Column
    private boolean data11UseChart;

    @Column
    private boolean data12UseChart;

    @Column
    private boolean data13UseChart;

    @Column
    private boolean data14UseChart;

    @Column
    private boolean data15UseChart;

    @Column
    private boolean data16UseChart;

    @Column
    private boolean data17UseChart;

    @Column
    private boolean data18UseChart;

    @Column
    private boolean data19UseChart;


    public ProjectMetadata() {
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public long getNodeNumber() {
        return nodeNumber;
    }

    public void setNodeNumber(long nodeNumber) {
        this.nodeNumber = nodeNumber;
    }

    public long getDataNumber() {
        return dataNumber;
    }

    public void setDataNumber(long nataNumber) {
        this.dataNumber = nataNumber;
    }

    public String getData0Desc() {
        return data0Desc;
    }

    public void setData0Desc(String data0Desc) {
        this.data0Desc = data0Desc;
    }

    public String getData1Desc() {
        return data1Desc;
    }

    public void setData1Desc(String data1Desc) {
        this.data1Desc = data1Desc;
    }

    public String getData2Desc() {
        return data2Desc;
    }

    public void setData2Desc(String data2Desc) {
        this.data2Desc = data2Desc;
    }

    public String getData3Desc() {
        return data3Desc;
    }

    public void setData3Desc(String data3Desc) {
        this.data3Desc = data3Desc;
    }

    public String getData4Desc() {
        return data4Desc;
    }

    public void setData4Desc(String data4Desc) {
        this.data4Desc = data4Desc;
    }

    public String getData5Desc() {
        return data5Desc;
    }

    public void setData5Desc(String data5Desc) {
        this.data5Desc = data5Desc;
    }

    public String getData6Desc() {
        return data6Desc;
    }

    public void setData6Desc(String data6Desc) {
        this.data6Desc = data6Desc;
    }

    public String getData7Desc() {
        return data7Desc;
    }

    public void setData7Desc(String data7Desc) {
        this.data7Desc = data7Desc;
    }

    public String getData8Desc() {
        return data8Desc;
    }

    public void setData8Desc(String data8Desc) {
        this.data8Desc = data8Desc;
    }

    public String getData9Desc() {
        return data9Desc;
    }

    public void setData9Desc(String data9Desc) {
        this.data9Desc = data9Desc;
    }

    public String getData10Desc() {
        return data10Desc;
    }

    public void setData10Desc(String data10Desc) {
        this.data10Desc = data10Desc;
    }

    public String getData11Desc() {
        return data11Desc;
    }

    public void setData11Desc(String data11Desc) {
        this.data11Desc = data11Desc;
    }

    public String getData12Desc() {
        return data12Desc;
    }

    public void setData12Desc(String data12Desc) {
        this.data12Desc = data12Desc;
    }

    public String getData13Desc() {
        return data13Desc;
    }

    public void setData13Desc(String data13Desc) {
        this.data13Desc = data13Desc;
    }

    public String getData14Desc() {
        return data14Desc;
    }

    public void setData14Desc(String data14Desc) {
        this.data14Desc = data14Desc;
    }

    public String getData15Desc() {
        return data15Desc;
    }

    public void setData15Desc(String data15Desc) {
        this.data15Desc = data15Desc;
    }

    public String getData16Desc() {
        return data16Desc;
    }

    public void setData16Desc(String data16Desc) {
        this.data16Desc = data16Desc;
    }

    public String getData17Desc() {
        return data17Desc;
    }

    public void setData17Desc(String data17Desc) {
        this.data17Desc = data17Desc;
    }

    public String getData18Desc() {
        return data18Desc;
    }

    public void setData18Desc(String data18Desc) {
        this.data18Desc = data18Desc;
    }

    public String getData19Desc() {
        return data19Desc;
    }

    public void setData19Desc(String data19Desc) {
        this.data19Desc = data19Desc;
    }

    public boolean isData0UseCard() {
        return data0UseCard;
    }

    public void setData0UseCard(boolean data0UseCard) {
        this.data0UseCard = data0UseCard;
    }

    public boolean isData1UseCard() {
        return data1UseCard;
    }

    public void setData1UseCard(boolean data1UseCard) {
        this.data1UseCard = data1UseCard;
    }

    public boolean isData2UseCard() {
        return data2UseCard;
    }

    public void setData2UseCard(boolean data2UseCard) {
        this.data2UseCard = data2UseCard;
    }

    public boolean isData3UseCard() {
        return data3UseCard;
    }

    public void setData3UseCard(boolean data3UseCard) {
        this.data3UseCard = data3UseCard;
    }

    public boolean isData4UseCard() {
        return data4UseCard;
    }

    public void setData4UseCard(boolean data4UseCard) {
        this.data4UseCard = data4UseCard;
    }

    public boolean isData5UseCard() {
        return data5UseCard;
    }

    public void setData5UseCard(boolean data5UseCard) {
        this.data5UseCard = data5UseCard;
    }

    public boolean isData6UseCard() {
        return data6UseCard;
    }

    public void setData6UseCard(boolean data6UseCard) {
        this.data6UseCard = data6UseCard;
    }

    public boolean isData7UseCard() {
        return data7UseCard;
    }

    public void setData7UseCard(boolean data7UseCard) {
        this.data7UseCard = data7UseCard;
    }

    public boolean isData8UseCard() {
        return data8UseCard;
    }

    public void setData8UseCard(boolean data8UseCard) {
        this.data8UseCard = data8UseCard;
    }

    public boolean isData9UseCard() {
        return data9UseCard;
    }

    public void setData9UseCard(boolean data9UseCard) {
        this.data9UseCard = data9UseCard;
    }

    public boolean isData10UseCard() {
        return data10UseCard;
    }

    public void setData10UseCard(boolean data10UseCard) {
        this.data10UseCard = data10UseCard;
    }

    public boolean isData11UseCard() {
        return data11UseCard;
    }

    public void setData11UseCard(boolean data11UseCard) {
        this.data11UseCard = data11UseCard;
    }

    public boolean isData12UseCard() {
        return data12UseCard;
    }

    public void setData12UseCard(boolean data12UseCard) {
        this.data12UseCard = data12UseCard;
    }

    public boolean isData13UseCard() {
        return data13UseCard;
    }

    public void setData13UseCard(boolean data13UseCard) {
        this.data13UseCard = data13UseCard;
    }

    public boolean isData14UseCard() {
        return data14UseCard;
    }

    public void setData14UseCard(boolean data14UseCard) {
        this.data14UseCard = data14UseCard;
    }

    public boolean isData15UseCard() {
        return data15UseCard;
    }

    public void setData15UseCard(boolean data15UseCard) {
        this.data15UseCard = data15UseCard;
    }

    public boolean isData16UseCard() {
        return data16UseCard;
    }

    public void setData16UseCard(boolean data16UseCard) {
        this.data16UseCard = data16UseCard;
    }

    public boolean isData17UseCard() {
        return data17UseCard;
    }

    public void setData17UseCard(boolean data17UseCard) {
        this.data17UseCard = data17UseCard;
    }

    public boolean isData18UseCard() {
        return data18UseCard;
    }

    public void setData18UseCard(boolean data18UseCard) {
        this.data18UseCard = data18UseCard;
    }

    public boolean isData19UseCard() {
        return data19UseCard;
    }

    public void setData19UseCard(boolean data19UseCard) {
        this.data19UseCard = data19UseCard;
    }

    public boolean isData0UseChart() {
        return data0UseChart;
    }

    public void setData0UseChart(boolean data0UseChart) {
        this.data0UseChart = data0UseChart;
    }

    public boolean isData1UseChart() {
        return data1UseChart;
    }

    public void setData1UseChart(boolean data1UseChart) {
        this.data1UseChart = data1UseChart;
    }

    public boolean isData2UseChart() {
        return data2UseChart;
    }

    public void setData2UseChart(boolean data2UseChart) {
        this.data2UseChart = data2UseChart;
    }

    public boolean isData3UseChart() {
        return data3UseChart;
    }

    public void setData3UseChart(boolean data3UseChart) {
        this.data3UseChart = data3UseChart;
    }

    public boolean isData4UseChart() {
        return data4UseChart;
    }

    public void setData4UseChart(boolean data4UseChart) {
        this.data4UseChart = data4UseChart;
    }

    public boolean isData5UseChart() {
        return data5UseChart;
    }

    public void setData5UseChart(boolean data5UseChart) {
        this.data5UseChart = data5UseChart;
    }

    public boolean isData6UseChart() {
        return data6UseChart;
    }

    public void setData6UseChart(boolean data6UseChart) {
        this.data6UseChart = data6UseChart;
    }

    public boolean isData7UseChart() {
        return data7UseChart;
    }

    public void setData7UseChart(boolean data7UseChart) {
        this.data7UseChart = data7UseChart;
    }

    public boolean isData8UseChart() {
        return data8UseChart;
    }

    public void setData8UseChart(boolean data8UseChart) {
        this.data8UseChart = data8UseChart;
    }

    public boolean isData9UseChart() {
        return data9UseChart;
    }

    public void setData9UseChart(boolean data9UseChart) {
        this.data9UseChart = data9UseChart;
    }

    public boolean isData10UseChart() {
        return data10UseChart;
    }

    public void setData10UseChart(boolean data10UseChart) {
        this.data10UseChart = data10UseChart;
    }

    public boolean isData11UseChart() {
        return data11UseChart;
    }

    public void setData11UseChart(boolean data11UseChart) {
        this.data11UseChart = data11UseChart;
    }

    public boolean isData12UseChart() {
        return data12UseChart;
    }

    public void setData12UseChart(boolean data12UseChart) {
        this.data12UseChart = data12UseChart;
    }

    public boolean isData13UseChart() {
        return data13UseChart;
    }

    public void setData13UseChart(boolean data13UseChart) {
        this.data13UseChart = data13UseChart;
    }

    public boolean isData14UseChart() {
        return data14UseChart;
    }

    public void setData14UseChart(boolean data14UseChart) {
        this.data14UseChart = data14UseChart;
    }

    public boolean isData15UseChart() {
        return data15UseChart;
    }

    public void setData15UseChart(boolean data15UseChart) {
        this.data15UseChart = data15UseChart;
    }

    public boolean isData16UseChart() {
        return data16UseChart;
    }

    public void setData16UseChart(boolean data16UseChart) {
        this.data16UseChart = data16UseChart;
    }

    public boolean isData17UseChart() {
        return data17UseChart;
    }

    public void setData17UseChart(boolean data17UseChart) {
        this.data17UseChart = data17UseChart;
    }

    public boolean isData18UseChart() {
        return data18UseChart;
    }

    public void setData18UseChart(boolean data18UseChart) {
        this.data18UseChart = data18UseChart;
    }

    public boolean isData19UseChart() {
        return data19UseChart;
    }

    public void setData19UseChart(boolean data19UseChart) {
        this.data19UseChart = data19UseChart;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProjectMetadata that = (ProjectMetadata) o;
        return id == that.id && nodeNumber == that.nodeNumber && dataNumber == that.dataNumber && data0UseCard == that.data0UseCard && data1UseCard == that.data1UseCard && data2UseCard == that.data2UseCard && data3UseCard == that.data3UseCard && data4UseCard == that.data4UseCard && data5UseCard == that.data5UseCard && data6UseCard == that.data6UseCard && data7UseCard == that.data7UseCard && data8UseCard == that.data8UseCard && data9UseCard == that.data9UseCard && data10UseCard == that.data10UseCard && data11UseCard == that.data11UseCard && data12UseCard == that.data12UseCard && data13UseCard == that.data13UseCard && data14UseCard == that.data14UseCard && data15UseCard == that.data15UseCard && data16UseCard == that.data16UseCard && data17UseCard == that.data17UseCard && data18UseCard == that.data18UseCard && data19UseCard == that.data19UseCard && data0UseChart == that.data0UseChart && data1UseChart == that.data1UseChart && data2UseChart == that.data2UseChart && data3UseChart == that.data3UseChart && data4UseChart == that.data4UseChart && data5UseChart == that.data5UseChart && data6UseChart == that.data6UseChart && data7UseChart == that.data7UseChart && data8UseChart == that.data8UseChart && data9UseChart == that.data9UseChart && data10UseChart == that.data10UseChart && data11UseChart == that.data11UseChart && data12UseChart == that.data12UseChart && data13UseChart == that.data13UseChart && data14UseChart == that.data14UseChart && data15UseChart == that.data15UseChart && data16UseChart == that.data16UseChart && data17UseChart == that.data17UseChart && data18UseChart == that.data18UseChart && data19UseChart == that.data19UseChart && Objects.equals(projectName, that.projectName) && Objects.equals(data0Desc, that.data0Desc) && Objects.equals(data1Desc, that.data1Desc) && Objects.equals(data2Desc, that.data2Desc) && Objects.equals(data3Desc, that.data3Desc) && Objects.equals(data4Desc, that.data4Desc) && Objects.equals(data5Desc, that.data5Desc) && Objects.equals(data6Desc, that.data6Desc) && Objects.equals(data7Desc, that.data7Desc) && Objects.equals(data8Desc, that.data8Desc) && Objects.equals(data9Desc, that.data9Desc) && Objects.equals(data10Desc, that.data10Desc) && Objects.equals(data11Desc, that.data11Desc) && Objects.equals(data12Desc, that.data12Desc) && Objects.equals(data13Desc, that.data13Desc) && Objects.equals(data14Desc, that.data14Desc) && Objects.equals(data15Desc, that.data15Desc) && Objects.equals(data16Desc, that.data16Desc) && Objects.equals(data17Desc, that.data17Desc) && Objects.equals(data18Desc, that.data18Desc) && Objects.equals(data19Desc, that.data19Desc);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, projectName, nodeNumber, dataNumber, data0Desc, data1Desc, data2Desc, data3Desc, data4Desc, data5Desc, data6Desc, data7Desc, data8Desc, data9Desc, data10Desc, data11Desc, data12Desc, data13Desc, data14Desc, data15Desc, data16Desc, data17Desc, data18Desc, data19Desc, data0UseCard, data1UseCard, data2UseCard, data3UseCard, data4UseCard, data5UseCard, data6UseCard, data7UseCard, data8UseCard, data9UseCard, data10UseCard, data11UseCard, data12UseCard, data13UseCard, data14UseCard, data15UseCard, data16UseCard, data17UseCard, data18UseCard, data19UseCard, data0UseChart, data1UseChart, data2UseChart, data3UseChart, data4UseChart, data5UseChart, data6UseChart, data7UseChart, data8UseChart, data9UseChart, data10UseChart, data11UseChart, data12UseChart, data13UseChart, data14UseChart, data15UseChart, data16UseChart, data17UseChart, data18UseChart, data19UseChart);
    }
}
