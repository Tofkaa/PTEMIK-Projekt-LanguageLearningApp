package com.languageapp.backend.entity;

import com.languageapp.backend.enums.MembershipStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "classroom_members", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"classroom_id", "user_id"})
})

public class ClassroomMember {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "classroom_member_id", updatable = false, nullable = false)
    private UUID classroomMemberId;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "classroom_id", nullable = false)
    private Classroom classroom;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @CreationTimestamp
    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MembershipStatus status = MembershipStatus.PENDING;
}