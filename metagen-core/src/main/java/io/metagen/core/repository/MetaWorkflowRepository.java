package io.metagen.core.repository;

import io.metagen.core.domain.MetaWorkflow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** Data access for {@link MetaWorkflow}. */
@Repository
public interface MetaWorkflowRepository extends JpaRepository<MetaWorkflow, UUID> {

    Optional<MetaWorkflow> findByName(String name);

    boolean existsByName(String name);

    /**
     * Fetch a workflow with all its states and transitions in one query.
     * Use this before executing a transition in the workflow engine.
     */
    @Query("""
        SELECT DISTINCT w FROM MetaWorkflow w
        LEFT JOIN FETCH w.states
        LEFT JOIN FETCH w.transitions t
        LEFT JOIN FETCH t.fromState
        LEFT JOIN FETCH t.toState
        WHERE w.id = :id
        """)
    Optional<MetaWorkflow> findByIdWithAll(@Param("id") UUID id);
}
