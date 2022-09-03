package com.hmdp;

import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
class HmDianPingApplicationTests {

    @Autowired
    private RedisIdWorker redisIdWorker;

    private ExecutorService executorService = Executors.newFixedThreadPool(500);
    @Test
    void testRedisIdWorker() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(300);
        long before = System.currentTimeMillis();
        //300个线程模拟300个用户
        for (int i = 0; i < 300; i++) {
            executorService.submit(()->{
                //每个线程执行100次,模拟每个用户100次订单
                for (int j = 0; j < 100; j++) {
                    long id = redisIdWorker.nextId("order");
                    System.out.println("id = " + id);
                }
                countDownLatch.countDown();;
            });
        }
        countDownLatch.await();
        long after = System.currentTimeMillis();
        long time = after - before;
        System.out.println("time = " + time);

    }


}
