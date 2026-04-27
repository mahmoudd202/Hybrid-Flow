package com.example.hybridflow.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "teams")
@Data
public class Team {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "office_id")
    private Office office;  //should that be removed??????? check Schedule class

    @Column(nullable = false, length = 150)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)  // I think OnetoOne is better, since a manager will take care of one team only
    @JoinColumn(name = "manager_id")
    private User manager;  // Team table owns the manager FK

//    @OneToMany(mappedBy = "team")
//    private List<User> employees;

//    @OneToMany(mappedBy = "team", cascade = CascadeType.ALL)
//    private List<Schedule> schedules = new ArrayList<>();

//    @ManyToMany(mappedBy = "participatingTeams")
//    private List<Meeting> meetings = new ArrayList<>();

}


