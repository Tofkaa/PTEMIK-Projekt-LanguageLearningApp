package com.languageapp.backend.entity;

import com.languageapp.backend.enums.AuthProvider;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;
import java.util.UUID;

@Entity @Getter @Setter @NoArgsConstructor
@Table(name="oauth_identities", uniqueConstraints={
        @UniqueConstraint(name="uk_oauth_provider_subject",columnNames={"provider","subject"}),
        @UniqueConstraint(name="uk_oauth_user_provider",columnNames={"user_id","provider"})})
public class OAuthIdentity {
    @Id @GeneratedValue(strategy=GenerationType.UUID)
    private UUID id;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20)
    private AuthProvider provider;
    @Column(nullable=false,length=255)
    private String subject;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="user_id",nullable=false)
    private User user;
    @CreationTimestamp @Column(nullable=false,updatable=false)
    private Instant linkedAt;
}
