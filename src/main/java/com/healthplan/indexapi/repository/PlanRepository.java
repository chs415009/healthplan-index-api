package com.healthplan.indexapi.repository;

import com.healthplan.indexapi.model.PlanEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface PlanRepository extends MongoRepository<PlanEntity, String> {

    // Find all documents by parent ID
    List<PlanEntity> findByParentId(String parentId);

    // Find all documents by object type
    List<PlanEntity> findByObjectType(String objectType);

    // Delete all documents by parent ID (for cascading delete)
    void deleteByParentId(String parentId);
}
