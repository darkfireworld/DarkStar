package org.dfw.darkstar.api.tcc;

/**
 * https://github.com/changmingxie/tcc-transaction
 */
public interface TccTransaction {
    /**
     * <pre>
     *     TRY:
     *          1. 完成所有业务检查（一致性）
     *          2. 预留必须业务资源（准隔离性）
     *          3. 如果抛出异常，则表示本次TCC事务执行失败
     *     CONFIRM:
     *          1. 真正执行业务
     *          2. 不作任何业务检查
     *          3. 只使用Try阶段预留的业务资源
     *          4. Confirm操作满足幂等性
     *     CANCEL:
     *          1. 释放Try阶段预留的业务资源
     *          2. Cancel操作满足幂等性
     * </pre>
     *
     * @param tccId    TCC_ID
     * @param tccName  TCC_NAME
     * @param tccState TCC_STATE
     * @param tccArgs  调用参数
     * @throws TccException Tcc异常
     */
    String transaction(String tccId, String tccName, TccState tccState, String tccArgs) throws TccException;
}
