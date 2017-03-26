package org.dfw.darkstar.api.tcc;

/**
 * TccService
 */
public interface TccService {

    /**
     * 执行一个TCC事务，如果TRY阶段发生异常，则抛出异常
     *
     * @param tccName 本次TCC事务的名称
     * @return TCC_ID
     * @throws TccException Try阶段发生异常（可能已经提交成功）
     */
    String start(String tccName) throws TccException;


    /**
     * 发起事务调用
     *
     * @param tccId              事务ID
     * @param tccTransactionName 事务
     * @param tccArgs            调用参数
     * @return 返回参数
     */
    Object exec(String tccId, String tccTransactionName, String tccArgs) throws TccException;


    /**
     * 提交本次事务
     *
     * @param tccId TCC_ID
     */
    void commit(String tccId) throws TccException;

    /**
     * 提交本次事务
     *
     * @param tccId TCC_ID
     */
    void cancel(String tccId) throws TccException;
}
