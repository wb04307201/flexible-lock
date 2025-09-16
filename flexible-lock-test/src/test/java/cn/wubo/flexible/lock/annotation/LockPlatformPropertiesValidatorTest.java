package cn.wubo.flexible.lock.annotation;

import cn.wubo.flexible.lock.propertes.LockPlatformProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class LockPlatformPropertiesValidatorTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void testValidRedisProperties() {
        LockPlatformProperties properties = new LockPlatformProperties();
        properties.setAlias("test-redis");
        properties.setLocktype("redis");
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("address", "localhost:6379");
        attributes.put("password", "password");
        attributes.put("database", 0);
        properties.setAttributes(attributes);

        Set<ConstraintViolation<LockPlatformProperties>> violations = validator.validate(properties);
        assertTrue(violations.isEmpty());
    }

    @Test
    void testInvalidRedisProperties_MissingAddress() {
        LockPlatformProperties properties = new LockPlatformProperties();
        properties.setAlias("test-redis");
        properties.setLocktype("redis");
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("password", "password");
        attributes.put("database", 0);
        properties.setAttributes(attributes);

        Set<ConstraintViolation<LockPlatformProperties>> violations = validator.validate(properties);
        assertFalse(violations.isEmpty());
        assertEquals(1, violations.size());
        assertTrue(violations.iterator().next().getMessage().contains("Redis address is required"));
    }

    @Test
    void testInvalidRedisProperties_MissingPassword() {
        LockPlatformProperties properties = new LockPlatformProperties();
        properties.setAlias("test-redis");
        properties.setLocktype("redis");
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("address", "localhost:6379");
        attributes.put("database", 0);
        properties.setAttributes(attributes);

        Set<ConstraintViolation<LockPlatformProperties>> violations = validator.validate(properties);
        assertFalse(violations.isEmpty());
        assertEquals(1, violations.size());
        assertTrue(violations.iterator().next().getMessage().contains("Redis password is required"));
    }

    @Test
    void testInvalidRedisProperties_InvalidDatabase() {
        LockPlatformProperties properties = new LockPlatformProperties();
        properties.setAlias("test-redis");
        properties.setLocktype("redis");
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("address", "localhost:6379");
        attributes.put("password", "password");
        attributes.put("database", 20); // Invalid database number
        properties.setAttributes(attributes);

        Set<ConstraintViolation<LockPlatformProperties>> violations = validator.validate(properties);
        assertFalse(violations.isEmpty());
        assertEquals(1, violations.size());
        assertTrue(violations.iterator().next().getMessage().contains("Redis database type is Integer and value between 0~15"));
    }

    @Test
    void testValidRedisClusterProperties() {
        LockPlatformProperties properties = new LockPlatformProperties();
        properties.setAlias("test-redis-cluster");
        properties.setLocktype("redis-cluster");
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("nodes", Arrays.asList("localhost:7000", "localhost:7001"));
        attributes.put("password", "password");
        properties.setAttributes(attributes);

        Set<ConstraintViolation<LockPlatformProperties>> violations = validator.validate(properties);
        assertTrue(violations.isEmpty());
    }

    @Test
    void testInvalidRedisClusterProperties_MissingNodes() {
        LockPlatformProperties properties = new LockPlatformProperties();
        properties.setAlias("test-redis-cluster");
        properties.setLocktype("redis-cluster");
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("password", "password");
        properties.setAttributes(attributes);

        Set<ConstraintViolation<LockPlatformProperties>> violations = validator.validate(properties);
        assertFalse(violations.isEmpty());
        assertEquals(1, violations.size());
        assertTrue(violations.iterator().next().getMessage().contains("Redis cluster nodes are required"));
    }

    @Test
    void testInvalidRedisClusterProperties_MissingPassword() {
        LockPlatformProperties properties = new LockPlatformProperties();
        properties.setAlias("test-redis-cluster");
        properties.setLocktype("redis-cluster");
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("nodes", Arrays.asList("localhost:7000", "localhost:7001"));
        properties.setAttributes(attributes);

        Set<ConstraintViolation<LockPlatformProperties>> violations = validator.validate(properties);
        assertFalse(violations.isEmpty());
        assertEquals(1, violations.size());
        assertTrue(violations.iterator().next().getMessage().contains("Redis cluster password is required"));
    }

    @Test
    void testValidRedisSentinelProperties() {
        LockPlatformProperties properties = new LockPlatformProperties();
        properties.setAlias("test-redis-sentinel");
        properties.setLocktype("redis-sentinel");
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("nodes", Arrays.asList("localhost:26379", "localhost:26380"));
        attributes.put("password", "password");
        attributes.put("masterName", "mymaster");
        properties.setAttributes(attributes);

        Set<ConstraintViolation<LockPlatformProperties>> violations = validator.validate(properties);
        assertTrue(violations.isEmpty());
    }

    @Test
    void testInvalidRedisSentinelProperties_MissingNodes() {
        LockPlatformProperties properties = new LockPlatformProperties();
        properties.setAlias("test-redis-sentinel");
        properties.setLocktype("redis-sentinel");
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("password", "password");
        attributes.put("masterName", "mymaster");
        properties.setAttributes(attributes);

        Set<ConstraintViolation<LockPlatformProperties>> violations = validator.validate(properties);
        assertFalse(violations.isEmpty());
        assertEquals(1, violations.size());
        assertTrue(violations.iterator().next().getMessage().contains("Redis sentinel nodes are required"));
    }

    @Test
    void testInvalidRedisSentinelProperties_MissingPassword() {
        LockPlatformProperties properties = new LockPlatformProperties();
        properties.setAlias("test-redis-sentinel");
        properties.setLocktype("redis-sentinel");
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("nodes", Arrays.asList("localhost:26379", "localhost:26380"));
        attributes.put("masterName", "mymaster");
        properties.setAttributes(attributes);

        Set<ConstraintViolation<LockPlatformProperties>> violations = validator.validate(properties);
        assertFalse(violations.isEmpty());
        assertEquals(1, violations.size());
        assertTrue(violations.iterator().next().getMessage().contains("Redis sentinel password is required"));
    }

    @Test
    void testInvalidRedisSentinelProperties_MissingMasterName() {
        LockPlatformProperties properties = new LockPlatformProperties();
        properties.setAlias("test-redis-sentinel");
        properties.setLocktype("redis-sentinel");
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("nodes", Arrays.asList("localhost:26379", "localhost:26380"));
        attributes.put("password", "password");
        properties.setAttributes(attributes);

        Set<ConstraintViolation<LockPlatformProperties>> violations = validator.validate(properties);
        assertFalse(violations.isEmpty());
        assertEquals(1, violations.size());
        assertTrue(violations.iterator().next().getMessage().contains("Redis sentinel masterName is required"));
    }

    @Test
    void testValidZookeeperProperties() {
        LockPlatformProperties properties = new LockPlatformProperties();
        properties.setAlias("test-zookeeper");
        properties.setLocktype("zookeeper");
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("connect", "localhost:2181");
        attributes.put("maxElapsedTimeMs", 10000);
        attributes.put("sleepMsBetweenRetries", 1000);
        attributes.put("root", "/locks");
        properties.setAttributes(attributes);

        Set<ConstraintViolation<LockPlatformProperties>> violations = validator.validate(properties);
        assertTrue(violations.isEmpty());
    }

    @Test
    void testInvalidZookeeperProperties_MissingConnect() {
        LockPlatformProperties properties = new LockPlatformProperties();
        properties.setAlias("test-zookeeper");
        properties.setLocktype("zookeeper");
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("maxElapsedTimeMs", 10000);
        attributes.put("sleepMsBetweenRetries", 1000);
        attributes.put("root", "/locks");
        properties.setAttributes(attributes);

        Set<ConstraintViolation<LockPlatformProperties>> violations = validator.validate(properties);
        assertFalse(violations.isEmpty());
        assertEquals(1, violations.size());
        assertTrue(violations.iterator().next().getMessage().contains("Zookeeper connect string is required"));
    }

    @Test
    void testInvalidZookeeperProperties_InvalidMaxElapsedTime() {
        LockPlatformProperties properties = new LockPlatformProperties();
        properties.setAlias("test-zookeeper");
        properties.setLocktype("zookeeper");
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("connect", "localhost:2181");
        attributes.put("maxElapsedTimeMs", -1); // Invalid value
        attributes.put("sleepMsBetweenRetries", 1000);
        attributes.put("root", "/locks");
        properties.setAttributes(attributes);

        Set<ConstraintViolation<LockPlatformProperties>> violations = validator.validate(properties);
        assertFalse(violations.isEmpty());
        assertEquals(1, violations.size());
        assertTrue(violations.iterator().next().getMessage().contains("Zookeeper maxElapsedTimeMs type is Integer and value > 0"));
    }

    @Test
    void testInvalidZookeeperProperties_InvalidSleepMsBetweenRetries() {
        LockPlatformProperties properties = new LockPlatformProperties();
        properties.setAlias("test-zookeeper");
        properties.setLocktype("zookeeper");
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("connect", "localhost:2181");
        attributes.put("maxElapsedTimeMs", 10000);
        attributes.put("sleepMsBetweenRetries", -1); // Invalid value
        attributes.put("root", "/locks");
        properties.setAttributes(attributes);

        Set<ConstraintViolation<LockPlatformProperties>> violations = validator.validate(properties);
        assertFalse(violations.isEmpty());
        assertEquals(1, violations.size());
        assertTrue(violations.iterator().next().getMessage().contains("Zookeeper sleepMsBetweenRetries type is Integer and value > 0"));
    }

    @Test
    void testInvalidZookeeperProperties_InvalidRoot() {
        LockPlatformProperties properties = new LockPlatformProperties();
        properties.setAlias("test-zookeeper");
        properties.setLocktype("zookeeper");
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("connect", "localhost:2181");
        attributes.put("maxElapsedTimeMs", 10000);
        attributes.put("sleepMsBetweenRetries", 1000);
        attributes.put("root", 123); // Invalid type
        properties.setAttributes(attributes);

        Set<ConstraintViolation<LockPlatformProperties>> violations = validator.validate(properties);
        assertFalse(violations.isEmpty());
        assertEquals(1, violations.size());
        assertTrue(violations.iterator().next().getMessage().contains("Zookeeper root type is String"));
    }

    @Test
    void testValidStandaloneProperties() {
        LockPlatformProperties properties = new LockPlatformProperties();
        properties.setAlias("test-standalone");
        properties.setLocktype("standalone");
        Map<String, Object> attributes = new HashMap<>();
        // Standalone doesn't require specific attributes
        properties.setAttributes(attributes);

        Set<ConstraintViolation<LockPlatformProperties>> violations = validator.validate(properties);
        assertTrue(violations.isEmpty());
    }

    @Test
    void testUnknownLockType() {
        LockPlatformProperties properties = new LockPlatformProperties();
        properties.setAlias("test-unknown");
        properties.setLocktype("unknown-type");
        Map<String, Object> attributes = new HashMap<>();
        properties.setAttributes(attributes);

        Set<ConstraintViolation<LockPlatformProperties>> violations = validator.validate(properties);
        assertTrue(violations.isEmpty()); // Should pass validation for unknown types
    }

    @Test
    void testAliasNotBlankValidation() {
        LockPlatformProperties properties = new LockPlatformProperties();
        properties.setAlias(""); // Blank alias should fail
        properties.setLocktype("standalone");

        Set<ConstraintViolation<LockPlatformProperties>> violations = validator.validate(properties);
        assertFalse(violations.isEmpty());
        assertEquals(1, violations.size());
        assertTrue(violations.iterator().next().getMessage().contains("不能为空")); // Chinese message for @NotBlank
    }

    @Test
    void testValidDefaultProperties() {
        LockPlatformProperties properties = new LockPlatformProperties();
        properties.setAlias("default-test");
        // Using default values for locktype ("standalone") and other fields

        Set<ConstraintViolation<LockPlatformProperties>> violations = validator.validate(properties);
        assertTrue(violations.isEmpty());
    }
}
