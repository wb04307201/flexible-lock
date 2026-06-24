package cn.wubo.flexible.lock.core;

import cn.wubo.flexible.lock.propertes.FlexibleLockProperties;

/**
 * 空操作锁后端：{@link #tryLock} 永远返回 {@code true}，{@link #unLock} 是 no-op。
 *
 * <p>用于在不修改业务代码的情况下关闭锁功能（{@code flexible.lock.lockType=none}），
 * 常见场景：
 * <ul>
 *   <li>本地开发或单元测试，避免配置 Redis/ZK；</li>
 *   <li>预发布环境想临时禁用锁以观察业务在无并发保护下的行为；</li>
 *   <li>保留 {@code @Locking} 注解作为文档/未来启用。</li>
 * </ul>
 *
 * <p>实现无任何状态，线程安全无开销。
 */
public class NoneLock extends AbstractLock{

    public NoneLock(FlexibleLockProperties properties) {
        super(properties);
    }

    @Override
    public Boolean tryLock(String key) {
        return true;
    }

    @Override
    public Boolean tryLock(String key, Long waitTime) {
        return true;
    }

    @Override
    public void unLock(String key) {

    }
}
