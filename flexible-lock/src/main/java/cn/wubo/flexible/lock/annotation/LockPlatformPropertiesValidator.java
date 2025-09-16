package cn.wubo.flexible.lock.annotation;

import cn.wubo.flexible.lock.propertes.LockPlatformProperties;
import cn.wubo.flexible.lock.utils.ValidationUtils;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.Map;
import java.util.stream.IntStream;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({TYPE})
@Retention(RUNTIME)
@Constraint(validatedBy = LockPlatformPropertiesValidator.Validator.class)
@Documented
public @interface LockPlatformPropertiesValidator {
    String message() default "Invalid lock platform properties";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<LockPlatformPropertiesValidator, LockPlatformProperties> {
        @Override
        public boolean isValid(LockPlatformProperties properties, ConstraintValidatorContext context) {
            if (properties == null) {
                return true; // Let other validators handle null
            }

            String lockType = properties.getLocktype();
            Map<String, Object> attributes = properties.getAttributes();

            return switch (lockType) {
                case "redis" -> validateRedisProperties(attributes, context);
                case "redis-cluster" -> validateRedisClusterProperties(attributes, context);
                case "redis-sentinel" -> validateRedisSentinelProperties(attributes, context);
                case "zookeeper" -> validateZookeeperProperties(attributes, context);
                case "standalone" -> validateStandaloneProperties(attributes, context);
                default -> true; // Unknown lock type, let other validators handle
            };
        }

        private boolean validateRedisProperties(Map<String, Object> attributes, ConstraintValidatorContext context) {
            if (ValidationUtils.validStringAttribute(attributes, "address")) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("Redis address is required")
                        .addPropertyNode("attributes[address]").addConstraintViolation();
                return false;
            }

            if (ValidationUtils.validStringAttribute(attributes, "password")) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("Redis password is required")
                        .addPropertyNode("attributes[password]").addConstraintViolation();
                return false;
            }

            if (ValidationUtils.validIntegerTypeAndRangeAttribute(attributes, "database", integer -> IntStream.rangeClosed(0, 15).noneMatch(v -> v == integer))) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("Redis database type is Integer and value between 0~15")
                        .addPropertyNode("attributes[database]").addConstraintViolation();
                return false;
            }

            return true;
        }

        private boolean validateRedisClusterProperties(Map<String, Object> attributes, ConstraintValidatorContext context) {
            if (ValidationUtils.validListStringAttribute(attributes, "nodes")) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("Redis cluster nodes are required")
                        .addPropertyNode("attributes[nodes]").addConstraintViolation();
                return false;
            }

            if (ValidationUtils.validStringAttribute(attributes, "password")) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("Redis cluster password is required")
                        .addPropertyNode("attributes[password]").addConstraintViolation();
                return false;
            }

            return true;
        }

        private boolean validateRedisSentinelProperties(Map<String, Object> attributes, ConstraintValidatorContext context) {
            if (ValidationUtils.validListStringAttribute(attributes, "nodes")) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("Redis sentinel nodes are required")
                        .addPropertyNode("attributes[nodes]").addConstraintViolation();
                return false;
            }

            if (ValidationUtils.validStringAttribute(attributes, "password")) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("Redis sentinel password is required")
                        .addPropertyNode("attributes[password]").addConstraintViolation();
                return false;
            }

            if (ValidationUtils.validStringAttribute(attributes, "masterName")) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("Redis sentinel masterName is required")
                        .addPropertyNode("attributes[masterName]").addConstraintViolation();
                return false;
            }

            return true;
        }

        private boolean validateZookeeperProperties(Map<String, Object> attributes, ConstraintValidatorContext context) {
            if (ValidationUtils.validStringAttribute(attributes, "connect")) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("Zookeeper connect string is required")
                        .addPropertyNode("attributes[connect]").addConstraintViolation();
                return false;
            }

            if (ValidationUtils.validIntegerTypeAndRangeAttribute(attributes, "maxElapsedTimeMs", integer -> integer > 0)) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("Zookeeper maxElapsedTimeMs type is Integer and value > 0")
                        .addPropertyNode("attributes[maxElapsedTimeMs]").addConstraintViolation();
                return false;
            }

            if (ValidationUtils.validIntegerTypeAndRangeAttribute(attributes, "sleepMsBetweenRetries", integer -> integer > 0)) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("Zookeeper sleepMsBetweenRetries type is Integer and value > 0")
                        .addPropertyNode("attributes[sleepMsBetweenRetries]").addConstraintViolation();
                return false;
            }

            if (ValidationUtils.validStringTypeAttribute(attributes, "root")) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("Zookeeper root type is String")
                        .addPropertyNode("attributes[root]").addConstraintViolation();
                return false;
            }

            return true;
        }

        private boolean validateStandaloneProperties(Map<String, Object> attributes, ConstraintValidatorContext context) {
            // Standalone lock doesn't require specific attributes
            return true;
        }
    }
}