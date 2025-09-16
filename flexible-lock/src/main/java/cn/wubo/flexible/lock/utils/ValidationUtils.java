package cn.wubo.flexible.lock.utils;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;


public class ValidationUtils {

    private ValidationUtils() {
    }

        /**
     * 验证目标对象是否符合约束条件
     *
     * @param validator 验证器实例，用于执行验证逻辑
     * @param target 待验证的目标对象
     * @param <T> 目标对象的类型
     * @throws ConstraintViolationException 当验证失败时抛出，包含所有违反约束的信息
     */
    public static <T> void validate(Validator validator, T target) {
        // 执行验证并获取违反约束的结果集合
        Set<ConstraintViolation<T>> violations = validator.validate(target);
        // 如果存在违反约束的情况，则抛出异常
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }

    public static Boolean validStringAttribute(Map<String, Object> attributes, String key) {
        return !attributes.containsKey(key) || !(attributes.get(key) instanceof String str) || str.trim().isEmpty();
    }

    public static Boolean validListStringAttribute(Map<String, Object> attributes, String key) {
        if (!attributes.containsKey(key) || !(attributes.get(key) instanceof List<?> list) || list.isEmpty())
            return true;

        for (Object item : (List<?>) attributes.get(key)) {
            if (!(item instanceof String str) || str.trim().isEmpty()) {
                return true;
            }
        }

        return false;
    }

    public static Boolean validIntegerTypeAndRangeAttribute(Map<String, Object> attributes, String key, Predicate<Integer> predicate) {
        return attributes.containsKey(key) && (!(attributes.get(key) instanceof Integer integer) || predicate.test(integer));
    }

    public static Boolean validStringTypeAttribute(Map<String, Object> attributes, String key) {
        return attributes.containsKey(key) && !(attributes.get(key) instanceof String);
    }


}
