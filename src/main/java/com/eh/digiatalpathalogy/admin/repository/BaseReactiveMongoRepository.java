package com.eh.digiatalpathalogy.admin.repository;

import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import reactor.core.publisher.Mono;

public interface BaseReactiveMongoRepository<T> {
    Mono<T> findAndModify(Query query, Update update, Class<T> clazz);

    Mono<T> findAndModify(Query query, T update);

    Mono<T> findAndModify(Query query, T update, boolean includeNulls);

    Mono<T> saveOrUpdate(Query query, T update);

    Mono<T> saveOrUpdate(Query query, Update update, Class<T> clazz);

    Mono<Long> updateMany(Query query, Update update, Class<T> clazz);

    Mono<Long> updateMany(Query query, T source);

}