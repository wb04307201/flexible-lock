package cn.wubo.flexible.lock;

import cn.wubo.flexible.lock.annotation.LockPlatformPropertiesValidator;
import cn.wubo.flexible.lock.propertes.LockPlatformProperties;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LockPlatformPropertiesValidatorTest {

    private Validator validator;
    private LockPlatformProperties properties;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
        properties = new LockPlatformProperties();
    }

    @Test
    void testValidStandaloneProperties() {
        properties.setAlias("test");
        properties.setLocktype("standalone");

        Set<ConstraintViolation<LockPlatformProperties>> violations = validator.validate(properties);
        assertTrue(violations.isEmpty());
    }

    @Test
    void testValidRedisProperties() {
        properties.setAlias("test");
        properties.setLocktype("redis");
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("address", "localhost:6379");
        attributes.put("password", "password");
        properties.setAttributes(attributes);

        Set<ConstraintViolation<LockPlatformProperties>> violations = validator.validate(properties);
        assertTrue(violations.isEmpty());
    }

    @Test
    void testInvalidRedisPropertiesMissingAddress() {
        properties.setAlias("test");
        properties.setLocktype("redis");
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("password", "password");
        properties.setAttributes(attributes);

        Set<ConstraintViolation<LockPlatformProperties>> violations = validator.validate(properties);
        assertFalse(violations.isEmpty());
        assertEquals(1, violations.size());
        assertTrue(violations.iterator().next().getMessage().contains("Redis address is required"));
    }

    @Test
    void testInvalidRedisPropertiesEmptyAddress() {
        properties.setAlias("test");
        properties.setLocktype("redis");
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("address", "   ");
        attributes.put("password", "password");
        properties.setAttributes(attributes);

        Set<ConstraintViolation<LockPlatformProperties>> violations = validator.validate(properties);
        assertFalse(violations.isEmpty());
        assertEquals(1, violations.size());
        assertTrue(violations.iterator().next().getMessage().contains("Redis address is required"));
    }

    @Test
    void testInvalidRedisPropertiesMissingPassword() {
        properties.setAlias("test");
        properties.setLocktype("redis");
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("address", "localhost:6379");
        properties.setAttributes(attributes);

        Set<ConstraintViolation<LockPlatformProperties>> violations = validator.validate(properties);
        assertFalse(violations.isEmpty());
        assertEquals(1, violations.size());
        assertTrue(violations.iterator().next().getMessage().contains("Redis password is required"));
    }

    @Test
    void testValidRedisClusterProperties() {
        properties.setAlias("test");
        properties.setLocktype("redis-cluster");
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("nodes", new String[]{"localhost:7000", "localhost:7001"});
        attributes.put("password", "password");
        properties.setAttributes(attributes);

        Set<ConstraintViolation<LockPlatformProperties>> violations = validator.validate(properties);
        assertTrue(violations.isEmpty());
    }

    @Test
    void testInvalidRedisClusterPropertiesMissingNodes() {
        properties.setAlias("test");
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
    void testInvalidRedisClusterPropertiesEmptyNodes() {
        properties.setAlias("test");
        properties.setLocktype("redis-cluster");
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("nodes", new String[]{});
        attributes.put("password", "password");
        properties.setAttributes(attributes);

        Set<ConstraintViolation<LockPlatformProperties>> violations = validator.validate(properties);
        assertFalse(violations.isEmpty());
        assertEquals(1, violations.size());
        assertTrue(violations.iterator().next().getMessage().contains("Redis cluster nodes are required"));
    }

    @Test
    void testInvalidRedisClusterPropertiesMissingPassword() {
        properties.setAlias("test");
        properties.setLocktype("redis-cluster");
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("nodes", new String[]{"localhost:7000", "localhost:7001"});
        properties.setAttributes(attributes);

        Set<ConstraintViolation<LockPlatformProperties>> violations = validator.validate(properties);
        assertFalse(violations.isEmpty());
        assertEquals(1, violations.size());
        assertTrue(violations.iterator().next().getMessage().contains("Redis cluster password is required"));
    }

    @Test
    void testValidRedisSentinelProperties() {
        properties.setAlias("test");
        properties.setLocktype("redis-sentinel");
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("nodes", new String[]{"localhost:26379", "localhost:26380"});
        attributes.put("password", "password");
        attributes.put("masterName", "mymaster");
        properties.setAttributes(attributes);

        Set<ConstraintViolation<LockPlatformProperties>> violations = validator.validate(properties);
        assertTrue(violations.isEmpty());
    }

    @Test
    void testInvalidRedisSentinelPropertiesMissingNodes() {
        properties.setAlias("test");
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
    void testInvalidRedisSentinelPropertiesMissingPassword() {
        properties.setAlias("test");
        properties.setLocktype("redis-sentinel");
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("nodes", new String[]{"localhost:26379", "localhost:26380"});
        attributes.put("masterName", "mymaster");
        properties.setAttributes(attributes);

        Set<ConstraintViolation<LockPlatformProperties>> violations = validator.validate(properties);
        assertFalse(violations.isEmpty());
        assertEquals(1, violations.size());
        assertTrue(violations.iterator().next().getMessage().contains("Redis sentinel password is required"));
    }

    @Test
    void testInvalidRedisSentinelPropertiesMissingMasterName() {
        properties.setAlias("test");
        properties.setLocktype("redis-sentinel");
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("nodes", new String[]{"localhost:26379", "localhost:26380"});
        attributes.put("password", "password");
        properties.setAttributes(attributes);

        Set<ConstraintViolation<LockPlatformProperties>> violations = validator.validate(properties);
        assertFalse(violations.isEmpty());
        assertEquals(1, violations.size());
        assertTrue(violations.iterator().next().getMessage().contains("Redis sentinel masterName is required"));
    }

    @Test
    void testInvalidRedisSentinelPropertiesEmptyMasterName() {
        properties.setAlias("test");
        properties.setLocktype("redis-sentinel");
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("nodes", new String[]{"localhost:26379", "localhost:26380"});
        attributes.put("password", "password");
        attributes.put("masterName", "   ");
        properties.setAttributes(attributes);

        Set<ConstraintViolation<LockPlatformProperties>> violations = validator.validate(properties);
        assertFalse(violations.isEmpty());
        assertEquals(1, violations.size());
        assertTrue(violations.iterator().next().getMessage().contains("Redis sentinel masterName is required"));
    }

    @Test
    void testValidZookeeperProperties() {
        properties.setAlias("test");
        properties.setLocktype("zookeeper");
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("connect", "localhost:2181");
        properties.setAttributes(attributes);

        Set<ConstraintViolation<LockPlatformProperties>> violations = validator.validate(properties);
        assertTrue(violations.isEmpty());
    }

    @Test
    void testInvalidZookeeperPropertiesMissingConnect() {
        properties.setAlias("test");
        properties.setLocktype("zookeeper");
        Map<String, Object> attributes = new HashMap<>();
        properties.setAttributes(attributes);

        Set<ConstraintViolation<LockPlatformProperties>> violations = validator.validate(properties);
        assertFalse(violations.isEmpty());
        assertEquals(1, violations.size());
        assertTrue(violations.iterator().next().getMessage().contains("Zookeeper connect string is required"));
    }

    @Test
    void testInvalidZookeeperPropertiesEmptyConnect() {
        properties.setAlias("test");
        properties.setLocktype("zookeeper");
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("connect", "   ");
        properties.setAttributes(attributes);

        Set<ConstraintViolation<LockPlatformProperties>> violations = validator.validate(properties);
        assertFalse(violations.isEmpty());
        assertEquals(1, violations.size());
        assertTrue(violations.iterator().next().getMessage().contains("Zookeeper connect string is required"));
    }

    @Test
    void testUnknownLockType() {
        properties.setAlias("test");
        properties.setLocktype("unknown");

        Set<ConstraintViolation<LockPlatformProperties>> violations = validator.validate(properties);
        assertTrue(violations.isEmpty()); // Should pass validation for unknown types
    }

    @Test
    void testNullProperties() {
        // Test that null properties don't cause issues
        LockPlatformPropertiesValidator.Validator validator = new LockPlatformPropertiesValidator.Validator();
        boolean result = validator.isValid(null, null);
        assertTrue(result); // Should return true for null properties
    }
}
