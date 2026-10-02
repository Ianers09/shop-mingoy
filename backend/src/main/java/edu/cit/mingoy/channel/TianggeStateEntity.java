package edu.cit.mingoy.channel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tiangge_state")
class TianggeStateEntity {

    @Id
    private Integer id;

    @Column(nullable = false)
    private Long cursor;

    protected TianggeStateEntity() {
    }

    TianggeStateEntity(Integer id, Long cursor) {
        this.id = id;
        this.cursor = cursor;
    }

    Long getCursor() {
        return cursor;
    }

    void setCursor(Long cursor) {
        this.cursor = cursor;
    }
}
