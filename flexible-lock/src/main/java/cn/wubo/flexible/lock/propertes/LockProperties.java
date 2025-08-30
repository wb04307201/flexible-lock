package cn.wubo.flexible.lock.propertes;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "lock")
public class LockProperties {
    List<LockPlatformProperties> props = new ArrayList<>();
}
