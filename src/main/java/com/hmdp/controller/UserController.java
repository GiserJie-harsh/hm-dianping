package com.hmdp.controller;


import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.entity.UserInfo;
import com.hmdp.service.IUserInfoService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;
    /**
     * 用户连续签到天数
     * @return com.hmdp.dto.Result
     * @author czj
     * @date 2022/12/9 19:35
     */
    @GetMapping("/sign/count")
    public Result signCount(){
        return userService.signCount();
    }
    /**
     * 用户签到
     * @return com.hmdp.dto.Result
     * @author czj
     * @date 2022/12/9 19:34
     */
    @PostMapping("/sign")
    public Result userSign(){
        return userService.userSign();
    }
    /**
     * 根据id查询用户
     * @param id
     * @return com.hmdp.dto.Result
     * @author czj
     * @date 2022/12/8 18:04
     */
    @GetMapping("/{id}")
    public Result getUserById(@PathVariable("id") Long id){
        User user = userService.getById(id);
        if (user == null) {
            Result.ok();
        }
        //进行数据脱敏，防止敏感数据泄露
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        return Result.ok(userDTO);
    }

    /**
     * 发送手机验证码
     */
    @PostMapping("code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        // 发送短信验证码并保存验证码
        return userService.sendCode(phone, session);
    }

    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session){
        return userService.userLogin(loginForm, session);
    }

    /**
     * 登出功能
     * @return 无
     */
    @PostMapping("/logout")
    public Result logout(){
        // TODO 实现登出功能
        return Result.fail("功能未完成");
    }
/**
 * 获取用户信息
 * @return com.hmdp.dto.Result
 * @author czj
 * @date 2022/12/2 8:46
 */

    @GetMapping("/me")
    public Result me(){
        return Result.ok(UserHolder.getUser());
    }

    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId){
        // 查询详情
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // 没有详情，应该是第一次查看详情
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        // 返回
        return Result.ok(info);
    }
}
