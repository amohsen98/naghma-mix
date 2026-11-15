package com.naghma.catalog.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Transactional outbox: a job message is written in the same transaction as the track
 * status change, then relayed to Kafka. A crash between the two can no longer lose a job
 * or publish one for a transaction that rolled back.
 */
@Entity
@Table(name = "outbox")
public class OutboxEvent extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public String topic;

    @Column(name = "msg_key")
    public String key;

    @Column(nullable = false, columnDefinition = "text")
    public String payload;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "sent_at")
    public Instant sentAt;

    @Column(nullable = false)
    public int attempts;
}
