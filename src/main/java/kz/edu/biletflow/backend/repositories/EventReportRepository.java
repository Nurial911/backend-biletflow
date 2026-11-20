package kz.edu.biletflow.backend.repositories;

import kz.edu.biletflow.backend.entities.EventReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface EventReportRepository extends JpaRepository<EventReport, Long>, JpaSpecificationExecutor<EventReport> {
    @Override
    @EntityGraph(attributePaths = {"event", "reporter"})
    Page<EventReport> findAll(Specification<EventReport> spec, Pageable pageable);

    boolean existsByEventIdAndReporterIdAndStatus(Long eventId, Long reporterId, EventReport.Status status);

    long countByStatus(EventReport.Status status);

    long countByEventIdAndStatus(Long eventId, EventReport.Status status);
}
