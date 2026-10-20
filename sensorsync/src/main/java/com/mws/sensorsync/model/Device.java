package com.mws.sensorsync.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Dispositivo IoT pertencente a um projeto. O dispositivo se autentica com um token;
 * o banco guarda apenas o hash SHA-256 do token.
 */
@Entity
@Table(name = "device",
        uniqueConstraints = @UniqueConstraint(name = "uk_device_project_index", columnNames = {"project_id", "device_index"}),
        indexes = @Index(name = "idx_device_token_hash", columnList = "token_hash", unique = true))
public class Device implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    /** Corresponde a Data.sensorIndex. */
    @Column(name = "device_index", nullable = false)
    private long deviceIndex;

    @Column(length = 254)
    private String name;

    @JsonIgnore
    @Column(name = "token_hash", length = 64)
    private String tokenHash;

    /** Primeiros caracteres do token, para o usuario identificar qual token esta em uso. */
    @Column(length = 16)
    private String tokenPrefix;

    @Column
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime lastSeenAt;

    public Device() {
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }
    public long getDeviceIndex() { return deviceIndex; }
    public void setDeviceIndex(long deviceIndex) { this.deviceIndex = deviceIndex; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
    public String getTokenPrefix() { return tokenPrefix; }
    public void setTokenPrefix(String tokenPrefix) { this.tokenPrefix = tokenPrefix; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(LocalDateTime lastSeenAt) { this.lastSeenAt = lastSeenAt; }

    @JsonProperty("projectID")
    public long getProjectID() { return project == null ? 0 : project.getId(); }

    @JsonProperty("hasToken")
    public boolean hasToken() { return tokenHash != null; }
}
