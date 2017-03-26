package org.dfw.darkstar.service.bank02;

import com.alibaba.fastjson.JSON;
import org.dfw.darkstar.api.bank02.TccTransactionBank02;
import org.dfw.darkstar.api.tcc.TccException;
import org.dfw.darkstar.api.tcc.TccState;
import org.dfw.darkstar.rpc.Rpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * TccTransactionBank02Service
 */
public class TccTransactionBank02Service implements TccTransactionBank02 {
    Logger logger = LoggerFactory.getLogger(this.getClass());

    static public void main(String[] args) throws Exception {
        Rpc.export(TccTransactionBank02.class, new TccTransactionBank02Service(), 2229);
    }

    public String transaction(String tccId, String tccName, TccState tccState, String tccArg) throws TccException {
        logger.info("{} - {} - {} - {}", tccId, tccName, tccState, JSON.toJSONString(tccArg));
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
