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

    @Column(length = 100)
    private String description;

    @Column
//For a different name in DB table, use: name = "NAME_IN_DB", and use nullable = false to restring null values, length = 100 limit to 100 char.
    private double data0;

    @Column
    private double data1;

    @Column
    private double data2;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DataPackage dataPackage = (DataPackage) o;
        return id == dataPackage.id && Double.compare(dataPackage.data0, data0) == 0 && Double.compare(dataPackage.data1, data1) == 0 && Double.compare(dataPackage.data2, data2) == 0 && Objects.equals(description, dataPackage.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, description, data0, data1, data2);
    }
}
