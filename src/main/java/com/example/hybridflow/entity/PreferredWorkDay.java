package com.example.hybridflow.entity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.DayOfWeek;
@Entity
@Table(name = "preferred_work_days")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreferredWorkDay {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
@lombok.EqualsAndHashCode.Exclude @lombok.ToString.Exclude
    private User user;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DayOfWeek dayOfWeek;
}
