package org.dfw.darkstar.service.tcc.runner;

import org.dfw.darkstar.api.tcc.TccService;
import org.dfw.darkstar.rpc.Rpc;
import org.dfw.darkstar.service.tcc.service.TccServiceImpl;

/**
 * 启动器
 */
public class TccServiceRunner {
    static public void main(String[] args) throws Exception {
        TccService tccService = new TccServiceImpl();
        Rpc.export(TccService.class, tccService, 10086);
    }
}
