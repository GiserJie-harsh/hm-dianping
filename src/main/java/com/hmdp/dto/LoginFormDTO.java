package com.hmdp.dto;

import lombok.Data;
/**
 * 
 * @param null
 * @return 登陆信息
 * @author czj
 * @date 2022/12/2 8:47
 */

@Data
public class LoginFormDTO {
    private String phone;
    private String code;
    private String password;
}
