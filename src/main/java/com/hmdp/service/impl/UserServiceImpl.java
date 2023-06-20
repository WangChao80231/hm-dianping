package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.omg.CORBA.TIMEOUT;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2. 不符合返回错误信息
            return Result.fail("手机号错误");
        }
        // 3.符合，生成验证码
        String code = RandomUtil.randomNumbers(6);
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone, code, 1, TimeUnit.MINUTES);
        // 4. 发送短信
        log.debug("发送短信成功，验证码为：{}", code);

        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1.校验手机号
        if (RegexUtils.isPhoneInvalid(loginForm.getPhone())) {
            // 2. 不符合返回错误信息
            return Result.fail("手机号错误");
        }
        // 2. 验证验证码，不一致报错
        String code = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + loginForm.getPhone());
        if (loginForm.getCode() != null && !loginForm.getCode().equals(code)) {
            return Result.fail("验证码错误");
        }
        // 3. 查询手机号是否存在
        User user = this.lambdaQuery().eq(User::getPhone, loginForm.getPhone()).one();
        if (user == null) {
            // 4. 不存在，注册。
           user = createUser(loginForm);
        }
        // 5. 保存到redis
        // 5.1 生成token，作为令牌
        String token = UUID.randomUUID().toString();
        // 5.2 将对象转化为hash
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> map = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create().ignoreNullValue().setFieldValueEditor((name,value)-> value.toString() ));
        // 5.3保存
        stringRedisTemplate.opsForHash().putAll(RedisConstants.LOGIN_USER_KEY + token,map);
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY + token, 30, TimeUnit.MINUTES);
        return Result.ok(token);
    }

    private User createUser(LoginFormDTO loginForm) {
        User user = new User();
        user.setPhone(loginForm.getPhone());
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        this.save(user);
        return user;
    }
}
