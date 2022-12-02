package com.hmdp.utils;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

/**
 * @author CZJ
 * @desc 拦截一切请求，进行登录校验
 * @create 2022-12-01 10:59
 */
public class RefreshTokenInterceptor implements HandlerInterceptor {
    private StringRedisTemplate redisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1.从请求中获取token
        String token = request.getHeader("authorization");
        //2.判断token是否为空以及判断用户是否已经存在于redis中
        if (StrUtil.isBlank(token)) {
            //不存在则放行
            return true;
        }
        //存在则
        Map<Object, Object> user = redisTemplate.opsForHash().entries(LOGIN_USER_KEY + token);
        //3.如果用户不存在(可能是该用户在redis中过期导致的)则拦截
        if (user.isEmpty()) {
            return true;
        }
        //4.将map类型再转换为DTO类型
        UserDTO userDTO = BeanUtil.fillBeanWithMap(user, new UserDTO(), false);
        //5.如果用户存在则将用户信息保存至threadLocal中，以便到时返回给浏览器
        UserHolder.saveUser(userDTO);
        //6.刷新token时间，如果不刷新的话，那么用户每30min就需要重新登录一次，这里也就是模拟了session的过期时间
        redisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //关闭资源
        UserHolder.removeUser();
    }
}
