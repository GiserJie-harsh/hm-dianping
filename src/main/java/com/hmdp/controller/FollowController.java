package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.service.IFollowService;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {
    @Autowired
    private IFollowService followService;
    /**
     * 用户关注
     * @param id
     * @param isFollow
     * @return com.hmdp.dto.Result
     * @author czj
     * @date 2022/12/8 19:08
     */

    @PutMapping("/{id}/{isFollow}")
    public Result addWatch(@PathVariable("id") Long id,@PathVariable("isFollow") boolean isFollow){
        return followService.add_watch(id,isFollow);
    }
    /**
     * 判断当前用户是否关注了该用户
     * @param followUserId
     * @return com.hmdp.dto.Result
     * @author czj
     * @date 2022/12/8 19:08
     */

    @GetMapping("/or/not/{id}")
    public Result isFollow(@PathVariable("id") Long followUserId) {
        return followService.isFollow(followUserId);
    }

    /**
     * 共同关注
     * @param id
     * @return com.hmdp.dto.Result
     * @author czj
     * @date 2022/12/8 19:09
     */
    @GetMapping("/common/{id}")
    public Result commonWatch(@PathVariable("id") Long id){
        return followService.commonWatch(id);
    }
}
