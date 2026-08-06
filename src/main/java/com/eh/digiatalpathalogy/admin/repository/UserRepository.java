package com.eh.digiatalpathalogy.admin.repository;

import com.eh.digiatalpathalogy.admin.entity.User;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

public interface UserRepository extends ReactiveMongoRepository<User, String>, BaseReactiveMongoRepository<User> {
    Mono<User> findByUsername(String username);
}
