package com.eh.digiatalpathalogy.admin.config;

import com.eh.digiatalpathalogy.admin.util.RedisEntityStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.resource.ClientResources;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisSentinelConfiguration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
public class RedisConfig {

    @Value("${app.redis.mode:standalone}")
    private String redisMode;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    private final RedisSentinelProps sentinelProps;

    public RedisConfig(RedisSentinelProps sentinelProps) {
        this.sentinelProps = sentinelProps;
    }

    @Bean(name = "customRedisConnectionFactory")
    @Primary
    @ConditionalOnProperty(name = "app.redis.mode", havingValue = "standalone", matchIfMissing = true)
    public ReactiveRedisConnectionFactory customRedisConnectionFactory(ObjectProvider<LettuceClientConfigurationBuilderCustomizer> lettuceCustomizer, ObjectProvider<ClientResources> clientResources,
                                                                       @Value("${spring.data.redis.host}") String redisHost,
                                                                       @Value("${spring.data.redis.port}") int redisPort) {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(redisHost, redisPort);
        if (redisPassword != null && !redisPassword.isEmpty()) {
            config.setPassword(RedisPassword.of(redisPassword));
        }
        return new LettuceConnectionFactory(config, buildLettuceClientConfig(lettuceCustomizer, clientResources));
    }

    @Bean(name = "customRedisConnectionFactory")
    @Primary
    @ConditionalOnProperty(name = "app.redis.mode", havingValue = "sentinel")
    public ReactiveRedisConnectionFactory sentinelReactiveRedisConnectionFactory(ObjectProvider<LettuceClientConfigurationBuilderCustomizer> customizers, ObjectProvider<ClientResources> clientResources) {
        RedisSentinelConfiguration sentinelConfig = new RedisSentinelConfiguration().master(sentinelProps.getMaster());
        sentinelProps.getNodes().forEach(node -> {
            String[] parts = node.split(":");
            if (parts.length < 2) {
                throw new IllegalArgumentException("Invalid Redis sentinel node format: " + node);
            }
            sentinelConfig.sentinel(parts[0], Integer.parseInt(parts[1]));
        });

        if (redisPassword != null && !redisPassword.isBlank()) {
            sentinelConfig.setPassword(RedisPassword.of(redisPassword));
        }

        return new LettuceConnectionFactory(sentinelConfig, buildLettuceClientConfig(customizers, clientResources));
    }

    private LettuceClientConfiguration buildLettuceClientConfig(ObjectProvider<LettuceClientConfigurationBuilderCustomizer> customizers, ObjectProvider<ClientResources> clientResources) {

        LettuceClientConfiguration.LettuceClientConfigurationBuilder builder = LettuceClientConfiguration.builder();
        clientResources.ifAvailable(builder::clientResources);
        customizers.orderedStream().forEach(c -> c.customize(builder));
        return builder.build();
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.deactivateDefaultTyping();
        return objectMapper;
    }

    @Bean
    public ReactiveRedisTemplate<String, Object> redisCache(@Qualifier("customRedisConnectionFactory") ReactiveRedisConnectionFactory factory, ObjectMapper objectMapper) {

        Jackson2JsonRedisSerializer<Object> serializer = new Jackson2JsonRedisSerializer<>(objectMapper, Object.class);
        RedisSerializationContext<String, Object> context = RedisSerializationContext
                .<String, Object>newSerializationContext(new StringRedisSerializer())
                .value(serializer)
                .build();

        return new ReactiveRedisTemplate<>(factory, context);
    }

    @Bean
    public RedisEntityStore redisEntityStoreNew(ReactiveRedisTemplate<String, Object> redisTemplate, ObjectMapper objectMapper) {
        return new RedisEntityStore(redisTemplate, objectMapper);
    }

    @Bean
    public LettuceClientConfigurationBuilderCustomizer lettuceCustomizer() {
        return builder -> builder.commandTimeout(Duration.ofSeconds(5))
                .clientOptions(ClientOptions.builder().autoReconnect(true)
                        .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                        .build());
    }

    @PostConstruct
    public void validateRedisConfig() {
        if ("sentinel".equalsIgnoreCase(redisMode) && sentinelProps.getNodes().isEmpty()) {
            throw new IllegalStateException("Redis mode is 'sentinel' but no sentinel nodes are configured");
        }
    }

}