package com.eh.digiatalpathalogy.admin.repository;

import com.eh.digiatalpathalogy.admin.entity.SlideAnalysisReport;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface SlideAnalysisReportRepository extends ReactiveMongoRepository<SlideAnalysisReport, String>, BaseReactiveMongoRepository<SlideAnalysisReport> {
    Mono<SlideAnalysisReport> findByAnalysisId(String analysisId);
    Flux<SlideAnalysisReport> findByDeviceSerialNumber(String deviceSerialNumber);
}
