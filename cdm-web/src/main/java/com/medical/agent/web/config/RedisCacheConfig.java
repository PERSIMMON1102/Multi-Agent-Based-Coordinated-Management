package com.medical.agent.web.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.medical.agent.service.cache.CacheNames;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.cache.CacheManagerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
@ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis")
public class RedisCacheConfig {

    @Bean
    public CacheManagerCustomizer<RedisCacheManager> redisCacheManagerCustomizer() {
        return cacheManager -> cacheManager.setTransactionAware(false);
    }

    @Bean
    public RedisCacheConfiguration redisCacheConfiguration() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        objectMapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder().allowIfSubType(Object.class).build(),
                ObjectMapper.DefaultTyping.EVERYTHING,
                JsonTypeInfo.As.PROPERTY
        );

        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(objectMapper);
        return RedisCacheConfiguration.defaultCacheConfig()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer))
                .disableCachingNullValues()
                .computePrefixWith(cacheName -> "cdm::" + cacheName + "::");
    }

    @Bean
    public RedisCacheManager redisCacheManager(org.springframework.data.redis.connection.RedisConnectionFactory connectionFactory,
                                               RedisCacheConfiguration redisCacheConfiguration) {
        Map<String, RedisCacheConfiguration> cacheConfigs = new LinkedHashMap<>();
        cacheConfigs.put(CacheNames.AUTH_CONFIG, redisCacheConfiguration.entryTtl(Duration.ofMinutes(30)));
        cacheConfigs.put(CacheNames.DOCTOR_DIRECTORY, redisCacheConfiguration.entryTtl(Duration.ofMinutes(10)));
        cacheConfigs.put(CacheNames.DOCTOR_DASHBOARD_OVERVIEW, redisCacheConfiguration.entryTtl(Duration.ofMinutes(1)));
        cacheConfigs.put(CacheNames.VECTOR_STATUS, redisCacheConfiguration.entryTtl(Duration.ofSeconds(30)));
        cacheConfigs.put(CacheNames.MEDPLUM_STATUS, redisCacheConfiguration.entryTtl(Duration.ofSeconds(30)));
        cacheConfigs.put(CacheNames.PATIENT_INTEGRATION_OVERVIEW, redisCacheConfiguration.entryTtl(Duration.ofMinutes(1)));
        cacheConfigs.put(CacheNames.PATIENT_INTEGRATION_STATUS, redisCacheConfiguration.entryTtl(Duration.ofMinutes(1)));
        cacheConfigs.put(CacheNames.CHAT_PATIENT_CONTEXT, redisCacheConfiguration.entryTtl(Duration.ofMinutes(2)));
        cacheConfigs.put(CacheNames.CHAT_RULE_ASSESSMENT, redisCacheConfiguration.entryTtl(Duration.ofSeconds(45)));
        cacheConfigs.put(CacheNames.CHAT_EVIDENCE_RETRIEVAL, redisCacheConfiguration.entryTtl(Duration.ofMinutes(2)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(redisCacheConfiguration)
                .withInitialCacheConfigurations(cacheConfigs)
                .build();
    }
}
