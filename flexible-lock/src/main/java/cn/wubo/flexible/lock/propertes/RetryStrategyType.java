package cn.wubo.flexible.lock.propertes;

public enum RetryStrategyType {

    //固定时间间隔重试 fixed, 指数退避重试 exponential, 随机退避重试 random

    FIXED,

    EXPONENTIAL,

    RANDOM;


}
