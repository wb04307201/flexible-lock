package cn.wubo.entity.sql;

import cn.wubo.flexible.lock.annotation.Locking;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DemoService {

    @Locking(alias = "test-1", keys = "#key")
    public String doWork1(String key) {
        try {
            Double time = (Math.random() * 30 + 1) * 1000;
            log.info(Thread.currentThread().getId() + " " + time);
            Thread.currentThread().wait(time.longValue());
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
        return key;
    }

    @Locking(alias = "test-2", keys = "#key")
    public String doWork2(String key) {
        try {
            Double time = (Math.random() * 30 + 1) * 1000;
            log.info(Thread.currentThread().getId() + " " + time);
            Thread.currentThread().wait(time.longValue());
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
        return key;
    }

    @Locking(alias = "test-3", keys = "#key")
    public String doWork3(String key) {
        try {
            Double time = (Math.random() * 30 + 1) * 1000;
            log.info("DemoService doWork3 thread：{} time:{}", Thread.currentThread().getId(), time);
            Thread.currentThread().wait(time.longValue());
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
        return key;
    }

    @Locking(alias = "test-4", keys = "#key")
    public String doWork4(String key) {
        try {
            Double time = (Math.random() * 30 + 1) * 1000;
            log.info("DemoService doWork3 thread：{} time:{}", Thread.currentThread().getId(), time);
            Thread.currentThread().wait(time.longValue());
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
        return key;
    }
}
