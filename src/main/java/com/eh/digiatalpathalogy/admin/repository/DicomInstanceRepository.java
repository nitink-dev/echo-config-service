package com.eh.digiatalpathalogy.admin.repository;

import com.eh.digiatalpathalogy.admin.entity.DicomInstance;
import com.eh.digiatalpathalogy.admin.entity.QaSlide;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface DicomInstanceRepository extends ReactiveMongoRepository<DicomInstance, String>,BaseReactiveMongoRepository<DicomInstance> {

    Flux<DicomInstance> findAllByBarcode(String barcode);

    Flux<DicomInstance> findAllByBarcodeAndSeriesInstanceUid(String barcode, String seriesInstanceUid);
}
