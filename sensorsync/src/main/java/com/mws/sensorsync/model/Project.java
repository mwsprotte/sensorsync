package com.mws.sensorsync.model;

import jakarta.persistence.*;

import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "project")
public class Project implements Serializable {

    private static final long serialVersionUID = 1L;


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;


    @Column(length = 254)
    private String name;

    @Column
    private long sensorNumber;

    @Column
    private long dataNumber;


    public Project() {
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getSensorNumber() {
        return sensorNumber;
    }

    public void setSensorNumber(long sensorNumber) {
        this.sensorNumber = sensorNumber;
    }

    public long getDataNumber() {
        return dataNumber;
    }

    public void setDataNumber(long dataNumber) {
        this.dataNumber = dataNumber;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Project project = (Project) o;
        return id == project.id && sensorNumber == project.sensorNumber && dataNumber == project.dataNumber && Objects.equals(name, project.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, sensorNumber, dataNumber);
    }
}
