package com.mcp.host.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AjaxResult {
    private int code;
    private String msg;
    private Object data;

    public static AjaxResult success(String msg) {
        return new AjaxResult(200, msg, null);
    }

    public static AjaxResult success(String msg, Object data) {
        return new AjaxResult(200, msg, data);
    }

    public static AjaxResult error(String msg) {
        return new AjaxResult(500, msg, null);
    }
}


