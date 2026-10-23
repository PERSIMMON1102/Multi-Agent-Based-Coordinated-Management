package com.medical.agent.model.mongo.repository;

import com.medical.agent.model.mongo.PaperCaseAccessLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaperCaseAccessLogRepository extends MongoRepository<PaperCaseAccessLog, String> {
    List<PaperCaseAccessLog> findByPaperCaseIdOrderByTimestampDesc(Long paperCaseId);
    List<PaperCaseAccessLog> findByPatientIdOrderByTimestampDesc(Long patientId);
    List<PaperCaseAccessLog> findByPaperCaseIdOrderByTimestampDesc(Long paperCaseId, Pageable pageable);
    List<PaperCaseAccessLog> findByPatientIdOrderByTimestampDesc(Long patientId, Pageable pageable);
    List<PaperCaseAccessLog> findAllByOrderByTimestampDesc(Pageable pageable);
}
