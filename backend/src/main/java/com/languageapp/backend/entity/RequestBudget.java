package com.languageapp.backend.entity;

import jakarta.persistence.*;
import lombok.NoArgsConstructor;

/** Opaque short-lived quota keys, never raw email addresses, IPs, passwords or tokens. */
@Entity @NoArgsConstructor
@Table(name="request_budgets", indexes=@Index(name="idx_request_budget_due", columnList="next_at"))
public class RequestBudget {
    @Id @Column(name="bucket_key",length=64) private String key;
    @Column(name="next_at",nullable=false) private long nextAt;
}
