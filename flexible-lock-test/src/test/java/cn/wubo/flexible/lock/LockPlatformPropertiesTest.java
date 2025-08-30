package cn.wubo.flexible.lock;

import cn.wubo.flexible.lock.propertes.LockPlatformProperties;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class LockPlatformPropertiesTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void testValidProperties() {
        LockPlatformProperties properties = new LockPlatformProperties();
        properties.setAlias("testAlias");
        properties.setLocktype("redis");
        properties.setRetryStrategy("fixed");

        assertTrue(validator.validate(properties).isEmpty());
    }

    @Test
    void testInvalidAlias() {
        LockPlatformProperties properties = new LockPlatformProperties();
        properties.setAlias(""); // 空字符串违反@NotBlank约束
        properties.setLocktype("redis");
        properties.setRetryStrategy("fixed");

        assertFalse(validator.validate(properties).isEmpty());
    }

    @Test
    void testInvalidLocktype() {
        LockPlatformProperties properties = new LockPlatformProperties();
        properties.setAlias("testAlias");
        properties.setLocktype("invalidType"); // 不符合正则表达式
        properties.setRetryStrategy("fixed");

        assertFalse(validator.validate(properties).isEmpty());
    }

    @Test
    void testInvalidRetryStrategy() {
        LockPlatformProperties properties = new LockPlatformProperties();
        properties.setAlias("testAlias");
        properties.setLocktype("redis");
        properties.setRetryStrategy("invalidStrategy"); // 不符合正则表达式

        assertFalse(validator.validate(properties).isEmpty());
    }

    @Test
    void testAttributesMap() {
        LockPlatformProperties properties = new LockPlatformProperties();
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("key1", "value1");
        attributes.put("key2", 123);

        properties.setAttributes(attributes);

        assertEquals("value1", properties.getAttributes().get("key1"));
        assertEquals(123, properties.getAttributes().get("key2"));
    }
}
