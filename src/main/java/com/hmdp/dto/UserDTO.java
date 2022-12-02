package com.hmdp.dto;

import lombok.Data;
/**
 * 
 * @param null
 * @return 简化用户信息，只保存较具代表性的部分
 * @author czj
 * @date 2022/12/2 8:47
 */

@Data
public class UserDTO {
    private Long id;
    private String nickName;
    private String icon;
}
