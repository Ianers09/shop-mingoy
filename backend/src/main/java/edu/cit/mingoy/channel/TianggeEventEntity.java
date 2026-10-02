package edu.cit.mingoy.channel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tiangge_events")
class TianggeEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 100)
    private String eventId;

    @Column(nullable = false)
    private Long seq;

    @Column(nullable = false, length = 40)
    private String type;

    protected TianggeEventEntity() {
    }

    TianggeEventEntity(String eventId, Long seq, String type) {
        this.eventId = eventId;
        this.seq = seq;
        this.type = type;
    }
}
