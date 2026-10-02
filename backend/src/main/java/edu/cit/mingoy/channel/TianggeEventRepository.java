package edu.cit.mingoy.channel;

import org.springframework.data.jpa.repository.JpaRepository;

interface TianggeEventRepository extends JpaRepository<TianggeEventEntity, Long> {
    boolean existsByEventId(String eventId);
}
