package com.example.hybridflow.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "office")
@Data
public class Office {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, length = 150)
    private String name;

//    @Column(length = 300)
//    private String address;

    @Column(name = "max_capacity", nullable = false)
    private int maxCapacity;

//    @OneToMany(mappedBy = "office", cascade = CascadeType.ALL, orphanRemoval = true)
//    private List<Team> teams = new ArrayList<>();

    // Added to track meetings happening in this office
//    @OneToMany(mappedBy = "office")
//    private List<Meeting> meetings = new ArrayList<>();
//
//    @OneToMany(mappedBy = "office")
//    private List<Schedule> schedules = new ArrayList<>();  //added this based on a decision that is not yet complete
//    // check schedule and team classes


}
