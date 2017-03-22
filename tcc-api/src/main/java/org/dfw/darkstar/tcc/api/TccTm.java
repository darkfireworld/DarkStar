package org.dfw.darkstar.tcc.api;

/**
 * TccTm
 */
public interface TccTm {

    /**
     * 执行一个TCC事务，如果TRY阶段发生异常，则抛出异常
     *
     * @param tccName 本次TCC事务的名称
     * @param tccId   本次TCC事务的ID
     * @param tccTxs  参数本次TCC事务的TCC对象
     * @param tccArgs 对应本次TCC事务TCC对象的参数
     * @throws TccException Try阶段发生异常（可能已经提交成功）
     */
    void execute(String tccName, String tccId, TccTx[] tccTxs, Object[] tccArgs) throws TccException;
}
