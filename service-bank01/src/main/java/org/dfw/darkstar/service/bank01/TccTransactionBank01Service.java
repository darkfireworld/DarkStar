package org.dfw.darkstar.service.bank01;

import com.alibaba.fastjson.JSON;
import org.dfw.darkstar.api.bank01.TccTransactionBank01;
import org.dfw.darkstar.api.tcc.TccException;
import org.dfw.darkstar.api.tcc.TccState;
import org.dfw.darkstar.rpc.Rpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TccTransactionBank01Service
 */
public class TccTransactionBank01Service implements TccTransactionBank01 {
    Logger logger = LoggerFactory.getLogger(this.getClass());

    static public void main(String[] args) throws Exception {
        Rpc.export(TccTransactionBank01.class, new TccTransactionBank01Service(), 2077);
    }

    public String transaction(String tccId, TccState tccState, String tccArg) throws TccException {
        logger.info("{} - {} - {} - {}", tccId, tccState, JSON.toJSONString(tccArg));
        switch (tccState) {
            case TCC_TRY: {

            }
            break;
            case TCC_CONFIRM: {

            }
            break;
            case TCC_CANCEL: {

            }
            break;
        }
        return null;
    }
}
