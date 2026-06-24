package cn.wubo.flexible.lock.core;

import cn.wubo.flexible.lock.propertes.FlexibleLockProperties;
import jakarta.validation.Valid;

/**
 * 抽象基类，把 {@link #getRetryCount()} / {@link #getWaitTime()} 两个全局配置项
 * 的读取集中到一个地方，避免每个后端各自重复转发。
 *
 * <p>具体后端只需关注 {@link #tryLock} / {@link #unLock} / {@link #shutdown()}
 * 三个方法。配置属性通过构造器注入并在 {@code @Valid} 校验后使用。
 */
public abstract class AbstractLock implements ILock {

    protected final FlexibleLockProperties properties;

    protected AbstractLock(@Valid FlexibleLockProperties properties) {
        this.properties = properties;
    }

    @Override
    public Integer getRetryCount() {
        return properties.getRetryCount();
    }

    @Override
    public Long getWaitTime() {
        return properties.getWaitTime();
    }
}
