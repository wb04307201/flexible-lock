package cn.wubo.entity.sql;

import cn.wubo.flexible.lock.annotation.Locking;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DemoService {

    @Locking(key = "'doWork-' + #key")
    public String doWork(String key) {
        double time = (Math.random() * 30 + 1) * 1000;
        log.info("{} {}", Thread.currentThread().getId(), time);
        try {
            Thread.sleep((long) time);
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
            Thread.currentThread().interrupt(); // 恢复中断状态
            throw new RuntimeException(e);
        }
        return key;
    }

}
