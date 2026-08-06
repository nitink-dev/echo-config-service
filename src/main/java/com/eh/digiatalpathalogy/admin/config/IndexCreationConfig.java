package com.eh.digiatalpathalogy.admin.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.CollectionOptions;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.ReactiveIndexOperations;
import reactor.core.publisher.Mono;

import java.util.Map;

import static com.eh.digiatalpathalogy.admin.constant.DbCollections.*;

@Configuration
public class IndexCreationConfig {

    private static final Logger log = LoggerFactory.getLogger(IndexCreationConfig.class);

    private final ReactiveMongoTemplate mongoTemplate;

    @Autowired
    public IndexCreationConfig(ReactiveMongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    private Mono<Void> ensureCollectionExists(String collectionName) {
        return mongoTemplate.collectionExists(collectionName)
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        log.info("Collection [{}] already exists", collectionName);
                        return Mono.empty();
                    } else {
                        log.info("Creating collection [{}]", collectionName);
                        return mongoTemplate.createCollection(collectionName, CollectionOptions.empty()).then();
                    }
                });
    }

    private Mono<Void> createIndexForCollection(String collectionName, Map<String, Sort.Direction> indexFields, boolean unique) {
        ReactiveIndexOperations indexOps = mongoTemplate.indexOps(collectionName);
        String indexName = String.join("_", indexFields.keySet()) + "_idx";

        return indexOps.getIndexInfo()
                .collectList()
                .flatMap(existingIndexes -> {
                    boolean indexExists = existingIndexes.stream()
                            .anyMatch(idx -> idx.getName().equals(indexName) && (unique == idx.isUnique()));

                    if (indexExists) {
                        log.info("Index [{}] already exists on [{}]", indexName, collectionName);
                        return Mono.empty();
                    }

                    Index index = new Index();
                    indexFields.forEach(index::on);
                    index.named(indexName);
                    if (unique) index.unique();

                    log.info("Creating index [{}] on [{}] with fields {}", indexName, collectionName, indexFields.keySet());
                    return indexOps.createIndex(index).then();
                });
    }

    @PostConstruct
    public void setUp() {
        Mono.when(
                ensureCollectionExists(QA_SLIDE)
                        .then(createIndexForCollection(QA_SLIDE, Map.of("barcode", Sort.Direction.ASC), true)),

                ensureCollectionExists(SLIDE_SCANNER)
                        .then(createIndexForCollection(SLIDE_SCANNER, Map.of(
                                "deviceSerialNumber", Sort.Direction.ASC
                        ), true)),

                ensureCollectionExists(AUTH_USER)
                        .then(createIndexForCollection(AUTH_USER, Map.of("username", Sort.Direction.ASC), true)),

                ensureCollectionExists(SLIDE_SCAN_STATUS)
                        .then(createIndexForCollection(SLIDE_SCAN_STATUS, Map.of("scanStatus", Sort.Direction.ASC, "updatedAt", Sort.Direction.DESC), false)),

                ensureCollectionExists(SLIDE_SCAN_STATUS)
                        .then(createIndexForCollection(SLIDE_SCAN_STATUS, Map.of("slideBarcode", Sort.Direction.ASC), true))


        ).subscribe(
                unused -> {
                },
                error -> log.error("Index creation failed: {}", error.getMessage(), error),
                () -> log.info("Index creation completed successfully.")
        );
    }
}
