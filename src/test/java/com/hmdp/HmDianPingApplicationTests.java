package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    private ShopServiceImpl shopService;
    /**
     * 测试RedisData
     * @return void
     * @author czj
     * @date 2022/12/5 15:01
     */

    @Test
    void testRedisData() throws InterruptedException {
        shopService.saveShopToRedis(1l,10l);
    }


}
