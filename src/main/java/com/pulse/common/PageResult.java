package com.pulse.common;

import java.util.List;

/**
 * 分页查询统一结果封装。
 * 聚合当前页数据列表、总记录数、当前页码与每页大小，
 * 与 {@code com.pulse.common.Result} 配合，将分页接口结果以一致结构返回前端。
 *
 * @param <T> 数据元素类型
 */
public class PageResult<T> {
    private List<T> records;
    private long total;
    private long page;
    private long size;

    /**
     * 构造分页结果。
     *
     * @param records 当前页数据列表
     * @param total   总记录数
     * @param page    当前页码（从 1 开始）
     * @param size    每页大小
     * @param <T>     数据元素类型
     * @return 分页结果对象
     */
    public static <T> PageResult<T> of(List<T> records, long total, long page, long size) {
        PageResult<T> r = new PageResult<>();
        r.records = records;
        r.total = total;
        r.page = page;
        r.size = size;
        return r;
    }

    public List<T> getRecords() { return records; }
    public long getTotal() { return total; }
    public long getPage() { return page; }
    public long getSize() { return size; }
}
