package com.hmdp.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/blog")
public class BlogController {

    @Resource
    private IBlogService blogService;
    @Resource
    private IUserService userService;
/**
 * 用户关注的人的笔记列表滚动分页查询
 * @param max
 * @param offset
 * @return com.hmdp.dto.Result
 * @author czj RequestParam注解一旦设置了默认值，就可以允许前端不传此参数，直接用默认值
 * @date 2022/12/9 10:03
 */
    @GetMapping("/of/follow")
    public Result queryBlogOfFollow(
            @RequestParam("lastId") Long max, @RequestParam(value = "offset", defaultValue = "0") Integer offset){
        return blogService.queryBlogOfFollow(max, offset);
    }
/**
 * 根据用户id查询其所有笔记
 * @param current
 * @param id
 * @return com.hmdp.dto.Result
 * @author czj
 * @date 2022/12/8 18:17
 */

    @GetMapping("/of/user")
    public Result queryBlogByUserId(@RequestParam(value = "current",defaultValue = "1") Integer current,
                                    @RequestParam("id") Long id){
        return blogService.queryBlogByUserId(current, id);

    }
    /**
     * 笔记详情页的点赞排行榜功能
     * @param id
     * @return com.hmdp.dto.Result
     * @author czj
     * @date 2022/12/8 16:39
     */

    @GetMapping("/likes/{id}")
    public Result queryBlogLikes(@PathVariable("id") Long id) {
        return blogService.queryBlogLikes(id);
    }

/**
 * 保存当前用户的探店笔记并结合feed流推送给其粉丝
 * @param blog
 * @return com.hmdp.dto.Result
 * @author czj
 * @date 2022/12/8 16:29
 */
    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {//blog为笔记内容
        return blogService.saveBlog(blog);
    }

    /**
     * 用户点击点赞按钮，实现点赞或取消点赞
     * @param id
     * @return com.hmdp.dto.Result
     * @author czj
     * @date 2022/12/8 16:35
     */

    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        return blogService.likeBlog(id);
    }

    /**
     * 我的所有笔记
     * @param current
     * @return com.hmdp.dto.Result
     * @author czj
     * @date 2022/12/8 16:47
     */

    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }
/**
 * 首页按照笔记点赞数显示笔记
 * @param current 当前页数
 * @return com.hmdp.dto.Result
 * @author czj
 * @date 2022/12/8 16:42
 */
    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.queryHotBlog(current);
    }
    /**
     * 用户查看某一篇笔记
     * @param id
     * @return com.hmdp.dto.Result
     * @author czj
     * @date 2022/12/8 16:32
     */

    @GetMapping("/{id}")
    public Result queryBlogById(@PathVariable("id") Long id){
        return blogService.queryBlogByBlogIg(id);
    }
}
