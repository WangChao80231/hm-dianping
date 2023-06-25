package com.hmdp.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * <p>
 *  TODO
 * </p>
 *
 * @author wangchao
 * @date 2023/6/22 23:23
 */
@Data
public class RedisVo {
    private LocalDateTime expireTime;
    private Object data;
}
