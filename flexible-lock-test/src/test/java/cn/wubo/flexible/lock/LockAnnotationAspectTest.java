package cn.wubo.flexible.lock;

import cn.wubo.flexible.lock.annotation.Locking;
import cn.wubo.flexible.lock.aop.LockAnnotationAspect;
import cn.wubo.flexible.lock.lock.ILock;
import cn.wubo.flexible.lock.retry.IRetryStrategy;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.BeanFactory;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class LockAnnotationAspectTest {

    @Mock
    private ILock mockLock;

    @Mock
    private IRetryStrategy mockRetryStrategy;

    @Mock
    private BeanFactory mockBeanFactory;

    @Mock
    private JoinPoint mockJoinPoint;

    @Mock
    private MethodSignature mockMethodSignature;

    private LockAnnotationAspect lockAnnotationAspect;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        List<ILock> locks = Arrays.asList(mockLock);
        List<IRetryStrategy> retryStrategies = Arrays.asList(mockRetryStrategy);
        lockAnnotationAspect = new LockAnnotationAspect(locks, mockBeanFactory);
    }

    @Test
    void testBeforeWithValidLock() throws NoSuchMethodException {
        // 准备测试数据
        TestService testService = new TestService();
        Method method = testService.getClass().getMethod("testMethod");

        when(mockJoinPoint.getSignature()).thenReturn(mockMethodSignature);
        when(mockMethodSignature.getMethod()).thenReturn(method);
        when(mockJoinPoint.getTarget()).thenReturn(testService);
        when(mockJoinPoint.getArgs()).thenReturn(new Object[]{});

        Locking locking = new Locking() {
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return Locking.class;
            }

            @Override
            public String alias() {
                return "testAlias";
            }

            @Override
            public String[] keys() {
                return new String[]{};
            }

            @Override
            public long time() {
                return -1;
            }

            @Override
            public TimeUnit unit() {
                return TimeUnit.SECONDS;
            }
        };

        when(mockLock.supportsAlias("testAlias")).thenReturn(true);
        when(mockLock.tryLock(anyString())).thenReturn(true);

        // 执行测试
        assertDoesNotThrow(() -> lockAnnotationAspect.before(mockJoinPoint, locking));

        // 验证
        verify(mockLock, times(1)).tryLock(anyString());
    }

    @Test
    void testAfterWithValidLock() throws NoSuchMethodException {
        // 准备测试数据
        TestService testService = new TestService();
        Method method = testService.getClass().getMethod("testMethod");

        when(mockJoinPoint.getSignature()).thenReturn(mockMethodSignature);
        when(mockMethodSignature.getMethod()).thenReturn(method);
        when(mockJoinPoint.getTarget()).thenReturn(testService);
        when(mockJoinPoint.getArgs()).thenReturn(new Object[]{});

        Locking locking = new Locking() {
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return Locking.class;
            }

            @Override
            public String alias() {
                return "testAlias";
            }

            @Override
            public String[] keys() {
                return new String[]{};
            }

            @Override
            public long time() {
                return -1;
            }

            @Override
            public TimeUnit unit() {
                return TimeUnit.SECONDS;
            }
        };

        when(mockLock.supportsAlias("testAlias")).thenReturn(true);

        // 执行测试
        assertDoesNotThrow(() -> lockAnnotationAspect.after(mockJoinPoint, locking));

        // 验证
        verify(mockLock, times(1)).unLock(anyString());
    }

    // 测试服务类
    static class TestService {
        public void testMethod() {
            // 测试方法
        }
    }
}
