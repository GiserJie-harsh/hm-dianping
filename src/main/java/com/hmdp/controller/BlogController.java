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
 * 保存当前用户的探店笔记
 * @param blog
 * @return com.hmdp.dto.Result
 * @author czj
 * @date 2022/12/8 16:29
 */
    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {//blog为笔记内容
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());//指定笔记所有者
        // 保存探店博文
        blogService.save(blog);
        // 返回id
        return Result.ok(blog.getId());//返回笔记id
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
