package com.languageapp.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

/** Teacher-defined timing for one learner and assignment; contains no medical information. */
@Entity
@Table(name = "assignment_accommodations", uniqueConstraints =
        @UniqueConstraint(name = "uk_assignment_accommodation", columnNames = {"assignment_id", "user_id"}))
@Getter @Setter @NoArgsConstructor
public class AssignmentAccommodation {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "assignment_id", nullable = false)
    private ClassroomAssignment assignment;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(nullable = false)
    private int extraMinutes;
    @Column(nullable = false)
    private boolean unlimited;
    private Instant availableUntilOverride;
}
