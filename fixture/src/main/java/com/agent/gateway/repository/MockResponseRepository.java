package com.agent.gateway.repository;

import com.agent.gateway.entity.MockResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MockResponseRepository extends JpaRepository<MockResponse, Long> {
    
    List<MockResponse> findByMockEndpointId(Long mockEndpointId);
    
    List<MockResponse> findByMockEndpointIdAndEnabled(Long mockEndpointId, Boolean enabled);
    
    @Query("SELECT mr FROM MockResponse mr WHERE mr.mockEndpoint.id = ?1 AND mr.enabled = true ORDER BY mr.priority DESC")
    List<MockResponse> findByMockEndpointIdOrderByPriorityDesc(Long mockEndpointId);
}
