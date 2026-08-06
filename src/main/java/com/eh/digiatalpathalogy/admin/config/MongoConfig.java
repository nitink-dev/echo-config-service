package com.eh.digiatalpathalogy.admin.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.ReadPreference;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.ReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.config.EnableReactiveMongoAuditing;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.convert.DefaultMongoTypeMapper;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableReactiveMongoAuditing
public class MongoConfig {

    private static final Logger log = LoggerFactory.getLogger(MongoConfig.class);

    @Value("${spring.data.mongodb.uri}")
    private String uri;

    @Value("${spring.data.mongodb.username:#{''}}")
    private String username;

    @Value("${spring.data.mongodb.password:#{''}}")
    private String password;

    @Value("${spring.data.mongodb.database}")
    private String databaseName;

    @Value("${spring.data.mongodb.authentication-database:}")
    private String authenticationDatabase;

    // ---- Timeouts & flags ----
    @Value("${spring.data.mongodb.connect-timeout:1000}")
    private int connectTimeoutMs;

    @Value("${spring.data.mongodb.socket-timeout:1000}")
    private int socketTimeoutMs;

    @Value("${spring.data.mongodb.retry-writes:false}")
    private boolean retryWrites;

    // ---- Pool tuning ----
    @Value("${spring.data.mongodb.pool.max-size:100}")
    private int poolMaxSize;

    @Value("${spring.data.mongodb.pool.min-size:10}")
    private int poolMinSize;

    @Value("${spring.data.mongodb.pool.max-wait-time-ms:1000}")
    private int poolMaxWaitTimeMs;

    @Bean(destroyMethod = "close")
    public MongoClient reactiveMongoClient() {
        ConnectionString connectionString = new ConnectionString(uri);

        MongoClientSettings.Builder settingsBuilder = MongoClientSettings.builder()
                .applyConnectionString(connectionString).retryWrites(retryWrites)
                .readPreference(ReadPreference.primary())
                .applyToSocketSettings(s -> s.connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
                        .readTimeout(socketTimeoutMs, TimeUnit.MILLISECONDS))
                .applyToClusterSettings(c -> c.serverSelectionTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS))
                .applyToConnectionPoolSettings(pool -> pool.maxSize(poolMaxSize)
                        .minSize(poolMinSize).maxWaitTime(poolMaxWaitTimeMs, TimeUnit.MILLISECONDS));

        if (!username.isEmpty() && !password.isEmpty()) {
            settingsBuilder.credential(
                    MongoCredential.createCredential(username, "admin", password.toCharArray())
            );
            log.info("Reactive MongoDB username configured: {}", username);
        }

        return MongoClients.create(settingsBuilder.build());
    }

    @Bean(name = "reactiveMongoDatabaseFactory")
    public ReactiveMongoDatabaseFactory reactiveMongoDatabaseFactory(MongoClient reactiveMongoClient) {
        return new SimpleReactiveMongoDatabaseFactory(reactiveMongoClient, databaseName);
    }

    @Bean(name = "reactiveMongoTemplate")
    public ReactiveMongoTemplate reactiveMongoTemplate(@Qualifier("reactiveMongoDatabaseFactory") ReactiveMongoDatabaseFactory factory, MappingMongoConverter mappingMongoConverter) {
        return new ReactiveMongoTemplate(factory, mappingMongoConverter);
    }

    @Bean
    public MappingMongoConverter mappingMongoConverter(MongoMappingContext context,
                                                       MongoCustomConversions conversions) {
        context.setAutoIndexCreation(false);
        MappingMongoConverter converter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, context);
        converter.setCustomConversions(conversions);
        converter.setTypeMapper(new DefaultMongoTypeMapper(null));
        return converter;
    }

}

