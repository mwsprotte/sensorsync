package com.mws.sensorsync.model;

import jakarta.persistence.*;
import org.hibernate.Length;

import java.io.Serializable;

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
    private long nataNumber;

    @Column(length = 15)
    private String data0Desc;

    @Column(length = 15)
    private String data1Desc;

    @Column(length = 15)
    private String data2Desc;

    @Column(length = 15)
    private String data3Desc;

    @Column(length = 15)
    private String data4Desc;

    @Column(length = 15)
    private String data5Desc;

    @Column(length = 15)
    private String data6Desc;

    @Column(length = 15)
    private String data7Desc;

    @Column(length = 15)
    private String data8Desc;

    @Column(length = 15)
    private String data9Desc;

    @Column(length = 15)
    private String data10Desc;

    @Column(length = 15)
    private String data11Desc;

    @Column(length = 15)
    private String data12Desc;

    @Column(length = 15)
    private String data13Desc;

    @Column(length = 15)
    private String data14Desc;

    @Column(length = 15)
    private String data15Desc;

    @Column(length = 15)
    private String data16Desc;

    @Column(length = 15)
    private String data17Desc;

    @Column(length = 15)
    private String data18Desc;

    @Column(length = 15)
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

}
