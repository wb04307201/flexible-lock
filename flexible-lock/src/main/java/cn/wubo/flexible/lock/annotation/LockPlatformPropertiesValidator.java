package cn.wubo.flexible.lock.annotation;

import cn.wubo.flexible.lock.propertes.LockPlatformProperties;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.Map;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({TYPE})
@Retention(RUNTIME)
@Constraint(validatedBy = LockPlatformPropertiesValidator.Validator.class)
@Documented
public @interface LockPlatformPropertiesValidator {
    class Validator implements ConstraintValidator<LockPlatformPropertiesValidator, LockPlatformProperties> {
        @Override
        public boolean isValid(LockPlatformProperties properties, ConstraintValidatorContext context) {
            if (properties == null) {
                return true; // Let other validators handle null
            }

            String lockType = properties.getLocktype();
            Map<String, Object> attributes = properties.getAttributes();

            switch (lockType) {
                case "redis":
                    return validateRedisProperties(attributes, context);
                case "redis-cluster":
                    return validateRedisClusterProperties(attributes, context);
                case "redis-sentinel":
                    return validateRedisSentinelProperties(attributes, context);
                case "zookeeper":
                    return validateZookeeperProperties(attributes, context);
                case "standalone":
                    return validateStandaloneProperties(attributes, context);
                default:
                    return true; // Unknown lock type, let other validators handle
            }
        }

        private boolean validateRedisProperties(Map<String, Object> attributes, ConstraintValidatorContext context) {
            if (!attributes.containsKey("address") || !(attributes.get("address") instanceof String) ||
                    ((String) attributes.get("address")).trim().isEmpty()) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("Redis address is required")
                        .addPropertyNode("attributes[address]").addConstraintViolation();
                return false;
            }

            if (!attributes.containsKey("password") || !(attributes.get("password") instanceof String)) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("Redis password is required")
                        .addPropertyNode("attributes[password]").addConstraintViolation();
                return false;
            }

            return true;
        }

        private boolean validateRedisClusterProperties(Map<String, Object> attributes, ConstraintValidatorContext context) {
            if (!attributes.containsKey("nodes") || !(attributes.get("nodes") instanceof String[]) ||
                    ((String[]) attributes.get("nodes")).length == 0) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("Redis cluster nodes are required")
                        .addPropertyNode("attributes[nodes]").addConstraintViolation();
                return false;
            }

            if (!attributes.containsKey("password") || !(attributes.get("password") instanceof String)) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("Redis cluster password is required")
                        .addPropertyNode("attributes[password]").addConstraintViolation();
                return false;
            }

            return true;
        }

        private boolean validateRedisSentinelProperties(Map<String, Object> attributes, ConstraintValidatorContext context) {
            if (!attributes.containsKey("nodes") || !(attributes.get("nodes") instanceof String[]) ||
                    ((String[]) attributes.get("nodes")).length == 0) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("Redis sentinel nodes are required")
                        .addPropertyNode("attributes[nodes]").addConstraintViolation();
                return false;
            }

            if (!attributes.containsKey("password") || !(attributes.get("password") instanceof String)) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("Redis sentinel password is required")
                        .addPropertyNode("attributes[password]").addConstraintViolation();
                return false;
            }

            if (!attributes.containsKey("masterName") || !(attributes.get("masterName") instanceof String) ||
                    ((String) attributes.get("masterName")).trim().isEmpty()) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("Redis sentinel masterName is required")
                        .addPropertyNode("attributes[masterName]").addConstraintViolation();
                return false;
            }

            return true;
        }

        private boolean validateZookeeperProperties(Map<String, Object> attributes, ConstraintValidatorContext context) {
            if (!attributes.containsKey("connect") || !(attributes.get("connect") instanceof String) ||
                    ((String) attributes.get("connect")).trim().isEmpty()) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("Zookeeper connect string is required")
                        .addPropertyNode("attributes[connect]").addConstraintViolation();
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