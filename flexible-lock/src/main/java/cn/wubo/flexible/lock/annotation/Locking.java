package cn.wubo.flexible.lock.annotation;

import java.lang.annotation.*;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface Locking {

    /**
     * 支持 SPEL expresion
     */
    String key();
    long waitTime() default -1;
    int retryCount() default -1;
}
