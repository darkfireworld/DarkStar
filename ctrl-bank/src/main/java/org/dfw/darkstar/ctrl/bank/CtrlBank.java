package org.dfw.darkstar.ctrl.bank;

import org.dfw.darkstar.api.bank01.TccTransactionBank01;
import org.dfw.darkstar.api.bank02.TccTransactionBank02;
import org.dfw.darkstar.api.tcc.TccService;
import org.dfw.darkstar.rpc.Rpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CtrlBank
 */
public class CtrlBank {
    static Logger logger = LoggerFactory.getLogger(CtrlBank.class);

    static public void main(String[] args) throws Exception {
        TccService tccService = (TccService) Rpc.refer(TccService.class);
        String tccId = null;
        try {
            tccId = tccService.start("测试");
            tccService.exec(tccId, TccTransactionBank01.class.getName(), "BANK01");
            tccService.exec(tccId, TccTransactionBank02.class.getName(), "BANK02");
            tccService.confirm(tccId);
        } catch (Throwable e) {
            if (tccId != null) {
                tccService.cancel(tccId);
            }
            logger.error(e.getMessage(), e);
        }
    }
}
