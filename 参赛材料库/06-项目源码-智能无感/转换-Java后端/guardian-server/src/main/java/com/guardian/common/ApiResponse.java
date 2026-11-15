package com.guardian.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应信封
 *
 * ★ 所有接口都返回这个结构，HTTP 状态码恒为 200，业务成败看 code
 *   前端只需判断 body.code === 200
 *
 * @param <T> 业务数据类型
 */
@Data
public class ApiResponse<T> implements Serializable {

    /** 业务码：200 成功，其他为错误 */
    private Integer code;

    /** 提示信息（中文，可直接展示给用户） */
    private String msg;

    /** 业务数据 */
    private T data;

    private ApiResponse(Integer code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public static <T> ApiResponse<T> ok() {
        return new ApiResponse<>(200, "ok", null);
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(200, "ok", data);
    }

    public static <T> ApiResponse<T> ok(String msg, T data) {
        return new ApiResponse<>(200, msg, data);
    }

    public static <T> ApiResponse<T> fail(String msg) {
        return new ApiResponse<>(400, msg, null);
    }

    public static <T> ApiResponse<T> fail(Integer code, String msg) {
        return new ApiResponse<>(code, msg, null);
    }
}
