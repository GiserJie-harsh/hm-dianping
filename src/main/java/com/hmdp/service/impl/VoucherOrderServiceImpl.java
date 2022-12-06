package com.hmdp.service.impl;

import cn.hutool.extra.template.engine.velocity.SimpleStringResourceLoader;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {


    @Autowired
    private SeckillVoucherServiceImpl seckillVoucherService;

    @Autowired
    private RedisIdWorker redisIdWorker;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result seckillVoucher(Long voucherId) {
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("活动未开始！！");
        }
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("活动已结束！！");
        }
        if(voucher.getStock() < 1){
            return Result.fail("库存不足！！");
        }
        Long userId = UserHolder.getUser().getId();
        //创建锁对象
        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        boolean isLock = lock.tryLock(1200);
        if (!isLock) {
            return Result.fail("不允许重复下单！！");
        }
        try {
            //获取代理对象
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } catch (IllegalStateException e) {
            throw new RuntimeException(e);
        } finally {
            //释放锁
            lock.unlock();
        }
    }

    @Transactional
    public  Result createVoucherOrder(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
            int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            //判断数据库是否存在当前用户订单
            if (count > 0) {
                // 用户已经购买过了
                return Result.fail("用户已经购买过一次！");
            }
            //更新数据库秒杀券的库存
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock - 1")
                    .eq("voucher_id", voucherId)
                    .gt("stock", 0)
                    .update();
            if (!success) {
                // 扣减失败
                return Result.fail("库存不足！");
            }
            //创建用户订单并写入数据库
            VoucherOrder order = new VoucherOrder();
            long id = redisIdWorker.nextId("order");
            UserDTO user = UserHolder.getUser();
            order.setVoucherId(voucherId);
            order.setId(id);
            order.setUserId(user.getId());
            save(order);
            //返回订单id
            return Result.ok(id);
//        }
    }
}
