package org.dfw.darkstar.service.tcc;

import org.dfw.darkstar.api.tcc.TccException;
import org.dfw.darkstar.api.tcc.TccService;
import org.dfw.darkstar.api.tcc.TccState;
import org.dfw.darkstar.api.tcc.TccTransaction;
import org.dfw.darkstar.rpc.Rpc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tcc 服务
 */
public class TccServiceImpl implements TccService {
    final Map<String, List<Tuple2<Class<? extends TccTransaction>, String>>> tccExecMap = new ConcurrentHashMap<String, List<Tuple2<Class<? extends TccTransaction>, String>>>(1024);
    Map<String, String> tccNameMap = new ConcurrentHashMap<String, String>(1024);

    public String start(String tccName) throws TccException {
        String tccId = UUID.randomUUID().toString();
        tccNameMap.put(tccId, tccName);
        return tccId;
    }

    synchronized public Object exec(String tccId, String tccTransactionName, String tccArgs) throws TccException {
        try {
            Class<? extends TccTransaction> tccTransactionCls = (Class<? extends TccTransaction>) Class.forName(tccTransactionName);
            TccTransaction tccTransaction = (TccTransaction) Rpc.refer(tccTransactionCls);
            List<Tuple2<Class<? extends TccTransaction>, String>> tccExecList = tccExecMap.get(tccId);
            if (tccExecList == null) {
                tccExecList = new ArrayList<Tuple2<Class<? extends TccTransaction>, String>>(16);
                tccExecMap.put(tccId, tccExecList);
            }
            tccExecList.add(new Tuple2<Class<? extends TccTransaction>, String>(tccTransactionCls, tccArgs));
            return tccTransaction.transaction(tccId, tccNameMap.get(tccId), TccState.TCC_TRY, tccArgs);
        } catch (Exception e) {
            throw new TccException(e);
        }
    }

    public void commit(String tccId) throws TccException {
        try {
            List<Tuple2<Class<? extends TccTransaction>, String>> tccExecList = tccExecMap.get(tccId);
            if (tccExecList != null) {
                for (Tuple2<Class<? extends TccTransaction>, String> wrap : tccExecList) {
                    TccTransaction tccTransaction = (TccTransaction) Rpc.refer(wrap._1);
                    tccTransaction.transaction(tccId, tccNameMap.get(tccId), TccState.TCC_CONFIRM, wrap._2);
                }
            }
        } catch (Exception e) {
            throw new TccException(e);
        }
    }

    public void cancel(String tccId) throws TccException {
        try {
            List<Tuple2<Class<? extends TccTransaction>, String>> tccExecList = tccExecMap.get(tccId);
            if (tccExecList != null) {
                for (Tuple2<Class<? extends TccTransaction>, String> wrap : tccExecList) {
                    TccTransaction tccTransaction = (TccTransaction) Rpc.refer(wrap._1);
                    tccTransaction.transaction(tccId, tccNameMap.get(tccId), TccState.TCC_CANCEL, wrap._2);
                }
            }
        } catch (Exception e) {
            throw new TccException(e);
        }
    }
}
