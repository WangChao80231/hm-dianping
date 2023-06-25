package com.hmdp;

import com.hmdp.service.IShopService;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.junit.rules.Stopwatch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.StopWatch;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    private ShopServiceImpl shopService;
    @Autowired
    private RedisIdWorker redisIdWorker;

    private ExecutorService executorService = Executors.newFixedThreadPool(500);

    @Test
    void  testSaveShop2Redis(){
        shopService.saveShop2Redis(1L, 10L);
    }

    @Test
    void  testNextId() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(300);
        StopWatch stopwatch = new StopWatch();

        Runnable runnable = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println(id);
            }
            countDownLatch.countDown();
        };
        stopwatch.start();
        for (int i = 0; i < 300; i++) {
            executorService.submit(runnable);
        }
        countDownLatch.await();
        stopwatch.stop();
        System.out.println(stopwatch.prettyPrint());
    }

}
