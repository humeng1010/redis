package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;

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
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Autowired
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryBlogById(Long id) {
        //1.查询blog
        Blog blog = this.getById(id);
        if (blog==null){
            return Result.fail("笔记不存在");
        }
        //2.查询blog有关的用户
        queryBlogUser(blog);
        //3.查询blog是否点赞
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = this.query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    private void isBlogLiked(Blog blog) {
        //1.获取当前登陆用户
        UserDTO user = UserHolder.getUser();
        //防止空指针异常
        if (user==null){
            //用户未登录 无需查询是否点赞
            return;
        }
        Long userId = user.getId();
        //2.判断当前登陆用户是否已经点赞
        String key = BLOG_LIKED_KEY+blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);

    }
    /**
     * 点赞功能
     * @param id
     * @return
     */
    @Override
    public Result likeBlog(Long id) {
        //1.获取当前登陆用户
        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();
        //2.判断当前登陆用户是否已经点赞
        String key = BLOG_LIKED_KEY+id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if (score == null){
            //3.如果没有点赞
            //3.1数据库点赞数+1
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            //3.2保存用户到redis的score_set集合 zadd key value score
            if (isSuccess){
                stringRedisTemplate.opsForZSet().add(key,userId.toString(),System.currentTimeMillis());
            }
        }else {
            //4.如果已经点赞,取消点赞
            //4.1数据库点赞数-1
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            //4.2把用户从redis的set集合中移除
            if (isSuccess){
                stringRedisTemplate.opsForZSet().remove(key,userId.toString());
            }
        }
        return Result.ok();
    }

    /**
     * 查询博客点赞列表 前五个
     * @param id 博客id
     * @return 点赞列表 前五个
     */
    @Override
    public Result queryBlogLikes(Long id) {
        //1.查询top5点赞用户 zrange key 0 4 得到用户id
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(BLOG_LIKED_KEY+id, 0, 4);
        log.info("top5:{}",top5);
        //2.解析出用户id
        //2.1如果top5为null或者为空
        if (top5 == null || top5.isEmpty()){
            //返回空集合
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        //3.根据用户id查询用户 select * from tb_user where id in ( #{ids} ) 由于in关键字没有顺序 所以导致原来的顺序失效了
//        List<User> users = userService.listByIds(ids);
        //解决办法 select * from tb_user where id in ( #{ids} ) order by field ( id, #{ids} )
        String idStr = StrUtil.join(",", ids);
        List<User> users = userService.query()
                .in("id",ids).last(StrUtil.format("order by field ( id,{} )",idStr)).list();

        List<UserDTO> userDTOList = users.stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());
        //返回
        return Result.ok(userDTOList);
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}
