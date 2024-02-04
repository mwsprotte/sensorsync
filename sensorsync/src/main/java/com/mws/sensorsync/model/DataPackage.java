package com.mws.sensorsync.model;

import jakarta.persistence.*;

import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "datapackage")
public class DataPackage implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(length = 254)
    private String description;

    @Column
    private long projectID;

    @Column
    private long nodeIndex;

    //For a different name in DB table, use: name = "NAME_IN_DB", and use nullable = false to restring null values, length = 100 limit to 100 char.
    @Column
    private double data0;

    @Column
    private double data1;

    @Column
    private double data2;

    @Column
    private double data3;

    @Column
    private double data4;

    @Column
    private double data5;

    @Column
    private double data6;

    @Column
    private double data7;

    @Column
    private double data8;

    @Column
    private double data9;

    @Column
    private double data10;

    @Column
    private double data11;

    @Column
    private double data12;

    @Column
    private double data13;

    @Column
    private double data14;

    @Column
    private double data15;

    @Column
    private double data16;

    @Column
    private double data17;

    @Column
    private double data18;


    @Column
    private double data19;


    public DataPackage() {
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public long getProjectID() {
        return projectID;
    }

    public void setProjectID(long projectID) {
        this.projectID = projectID;
    }

    public long getNodeIndex() {
        return nodeIndex;
    }

    public void setNodeIndex(long nodeIndex) {
        this.nodeIndex = nodeIndex;
    }

    public double getData0() {
        return data0;
    }

    public void setData0(double data0) {
        this.data0 = data0;
    }

    public double getData1() {
        return data1;
    }

    public void setData1(double data1) {
        this.data1 = data1;
    }

    public double getData2() {
        return data2;
    }

    public void setData2(double data2) {
        this.data2 = data2;
    }

    public double getData3() {
        return data3;
    }

    public void setData3(double data3) {
        this.data3 = data3;
    }

    public double getData4() {
        return data4;
    }

    public void setData4(double data4) {
        this.data4 = data4;
    }

    public double getData5() {
        return data5;
    }

    public void setData5(double data5) {
        this.data5 = data5;
    }

    public double getData6() {
        return data6;
    }

    public void setData6(double data6) {
        this.data6 = data6;
    }

    public double getData7() {
        return data7;
    }

    public void setData7(double data7) {
        this.data7 = data7;
    }

    public double getData8() {
        return data8;
    }

    public void setData8(double data8) {
        this.data8 = data8;
    }

    public double getData9() {
        return data9;
    }

    public void setData9(double data9) {
        this.data9 = data9;
    }

    public double getData10() {
        return data10;
    }

    public void setData10(double data10) {
        this.data10 = data10;
    }

    public double getData11() {
        return data11;
    }

    public void setData11(double data11) {
        this.data11 = data11;
    }

    public double getData12() {
        return data12;
    }

    public void setData12(double data12) {
        this.data12 = data12;
    }

    public double getData13() {
        return data13;
    }

    public void setData13(double data13) {
        this.data13 = data13;
    }

    public double getData14() {
        return data14;
    }

    public void setData14(double data14) {
        this.data14 = data14;
    }

    public double getData15() {
        return data15;
    }

    public void setData15(double data15) {
        this.data15 = data15;
    }

    public double getData16() {
        return data16;
    }

    public void setData16(double data16) {
        this.data16 = data16;
    }

    public double getData17() {
        return data17;
    }

    public void setData17(double data17) {
        this.data17 = data17;
    }

    public double getData18() {
        return data18;
    }

    public void setData18(double data18) {
        this.data18 = data18;
    }

    public double getData19() {
        return data19;
    }

    public void setData19(double data19) {
        this.data19 = data19;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DataPackage that = (DataPackage) o;
        return id == that.id && projectID == that.projectID && nodeIndex == that.nodeIndex && Double.compare(data0, that.data0) == 0 && Double.compare(data1, that.data1) == 0 && Double.compare(data2, that.data2) == 0 && Double.compare(data3, that.data3) == 0 && Double.compare(data4, that.data4) == 0 && Double.compare(data5, that.data5) == 0 && Double.compare(data6, that.data6) == 0 && Double.compare(data7, that.data7) == 0 && Double.compare(data8, that.data8) == 0 && Double.compare(data9, that.data9) == 0 && Double.compare(data10, that.data10) == 0 && Double.compare(data11, that.data11) == 0 && Double.compare(data12, that.data12) == 0 && Double.compare(data13, that.data13) == 0 && Double.compare(data14, that.data14) == 0 && Double.compare(data15, that.data15) == 0 && Double.compare(data16, that.data16) == 0 && Double.compare(data17, that.data17) == 0 && Double.compare(data18, that.data18) == 0 && Double.compare(data19, that.data19) == 0 && Objects.equals(description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, description, projectID, nodeIndex, data0, data1, data2, data3, data4, data5, data6, data7, data8, data9, data10, data11, data12, data13, data14, data15, data16, data17, data18, data19);
    }
}
