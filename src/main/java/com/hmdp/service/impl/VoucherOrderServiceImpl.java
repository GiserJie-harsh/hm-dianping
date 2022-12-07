package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {


    @Autowired
    private SeckillVoucherServiceImpl seckillVoucherService;

    @Autowired
    private RedisIdWorker redisIdWorker;//生成订单编号
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;//分布式锁

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;//执行lua脚本的必需品
    //阻塞队列
    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
    //线程池
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
    private IVoucherOrderService proxy;
    //读取lua脚本
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }
    //确保在该类初始化时就执行此方法
    @PostConstruct
    private void init() {
        //执行多线程任务
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }
    private class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            while (true) {//在此类初始化后就要死循环不断从队列中订单对象放入数据库中
                try {
                    //1.获取队列中的订单信息
                    VoucherOrder take = orderTasks.take();
                    //2.创建订单
                    handerVoucherOrder(take);

                } catch (Exception e) {
                    log.error("处理订单异常！！",e);
                }

            }
        }

    }

    private void handerVoucherOrder(VoucherOrder take) {
        //其实1-4 我们是可以省略的，因为我们已经有了Redission分布式锁，这里加锁只是为了兜底，防止Redis失效
        //1.获取用户,这里不能从ThreadLocal中获取了，因为已经更换线程了
        Long userId = take.getUserId();
        //2.创建锁对象
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        //3.获取锁
        boolean isLock = lock.tryLock();
        //4.判断是否获取锁成功
        if (!isLock) {
            log.error("不允许重复下单");
            return;
        }
        try {
            proxy.createVoucherOrder(take);//创建订单到数据库
        }
        catch (Exception e){
            log.error("这里出错了");
        } finally {
            //释放锁
            lock.unlock();
        }
    }
    /**
     * 秒杀业务优化，将判断是否具有购买资格（优化为使用lua脚本）和订单生成分线程执行
     * @param voucherId 优惠券ID
     * @return com.hmdp.dto.Result
     * @author czj
     * @date 2022/12/7 11:01
     */

    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        // 1.执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString());
        int r = result.intValue();
        // 2.判断结果是否为0
        if (r != 0) {
            // 2.1.不为0 ，代表没有购买资格
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        //为0证明有购买资格，创建订单放入阻塞队列中去
        VoucherOrder order = new VoucherOrder();
        //订单ID
        long orderId = redisIdWorker.nextId("order");
        order.setVoucherId(voucherId);
        order.setId(orderId);
        order.setUserId(userId);
        //放入阻塞队列
        orderTasks.add(order);
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        // 3.返回订单id
        return Result.ok(orderId);
    }

    @Transactional
    public  void createVoucherOrder(VoucherOrder voucherId) {
        Long userId = voucherId.getUserId();
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            //判断数据库是否存在当前用户订单
            if (count > 0) {
                log.error("用户已经购买一次了");//理论不会出现，因为lua脚本已经判断过了
                return;
            }
            //更新数据库秒杀券的库存
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock - 1")
                    .eq("voucher_id", voucherId.getVoucherId())
                    .gt("stock", 0)
                    .update();
            if (!success) {
                // 扣减失败
                log.error("库存不足！！！");//理论不会出现，因为lua脚本已经判断过了
                return;
            }
            //保存订单到数据库
            save(voucherId);
}
}
