package com.pandago.common.api;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 统一分页响应结构。
 *
 * <p>所有分页接口均返回该结构，字段固定，避免各模块分页格式不一致。
 * 阶段 2 接入 MyBatis-Plus 后，在 Service 层把 IPage 转换为本对象再返回。</p>
 *
 * @param <T> 列表元素类型
 */
@Data
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前页数据 */
    private List<T> list;

    /** 总记录数 */
    private long total;

    /** 当前页码（从 1 开始） */
    private long pageNum;

    /** 每页大小 */
    private long pageSize;

    /** 总页数 */
    private long pages;

    public static <T> PageResult<T> of(List<T> list, long total, long pageNum, long pageSize, long pages) {
        PageResult<T> result = new PageResult<>();
        result.setList(list);
        result.setTotal(total);
        result.setPageNum(pageNum);
        result.setPageSize(pageSize);
        result.setPages(pages);
        return result;
    }

    /** 空分页结果，用于无数据场景 */
    public static <T> PageResult<T> empty(long pageNum, long pageSize) {
        return of(List.of(), 0, pageNum, pageSize, 0);
    }
}
