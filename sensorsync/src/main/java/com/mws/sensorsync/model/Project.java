package com.mws.sensorsync.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

import java.io.Serializable;

@Entity
@Table(name = "project")
public class Project implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(length = 254)
    private String name;

    /** Quantidade de dispositivos (mantido em sincronia com a tabela device). */
    @Column
    private long sensorNumber;

    /** Quantidade de variaveis enviadas por dispositivo. */
    @Column
    private long dataNumber;

    /** Dono do projeto. Nulo apenas em bancos antigos; a migracao preenche. */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "owner_id")
    private UserHuman owner;

    public Project() {
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public long getSensorNumber() { return sensorNumber; }
    public void setSensorNumber(long sensorNumber) { this.sensorNumber = sensorNumber; }
    public long getDataNumber() { return dataNumber; }
    public void setDataNumber(long dataNumber) { this.dataNumber = dataNumber; }
    public UserHuman getOwner() { return owner; }
    public void setOwner(UserHuman owner) { this.owner = owner; }

    @JsonProperty("ownerLogin")
    public String getOwnerLogin() { return owner == null ? null : owner.getLogin(); }
}
