package org.dfw.darkstar.service.tcc.service;

import com.alibaba.druid.pool.DruidDataSource;
import com.google.common.base.Strings;
import com.sun.xml.internal.txw2.TxwException;
import org.dfw.darkstar.api.tcc.TccException;
import org.dfw.darkstar.api.tcc.TccService;
import org.dfw.darkstar.api.tcc.TccState;
import org.dfw.darkstar.api.tcc.TccTransaction;
import org.dfw.darkstar.rpc.Rpc;
import org.dfw.darkstar.service.tcc.io.mytcc.MyTcc;
import org.dfw.darkstar.service.tcc.io.mytcctx.MyTccTx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Tcc 服务
 */
public class TccServiceImpl implements TccService {
    Logger logger = LoggerFactory.getLogger(this.getClass());
    DruidDataSource druidDataSource;
    ScheduledExecutorService scheduledExecutorService;

    public TccServiceImpl() throws Exception {
        // jdbc
        druidDataSource = new DruidDataSource();
        druidDataSource.setUrl("jdbc:mysql://192.168.137.32:3306/tcc?useUnicode=true&characterEncoding=UTF-8");
        druidDataSource.setUsername("root");
        druidDataSource.setPassword("root");
        druidDataSource.setMaxActive(100);
        druidDataSource.setInitialSize(1);
        druidDataSource.setMaxWait(1000 * 10);
        druidDataSource.setMinIdle(1);
        druidDataSource.setTestWhileIdle(true);
        druidDataSource.setTestOnBorrow(false);
        druidDataSource.setTestOnReturn(false);
        druidDataSource.init();
        // scheduled
        scheduledExecutorService = Executors.newScheduledThreadPool(1);
        scheduledExecutorService.scheduleWithFixedDelay(new Runnable() {
            public void run() {
                try {
                    List<MyTcc> myTccList = new LinkedList<MyTcc>();
                    // 获取提交的tx
                    {
                        Connection connection = null;
                        try {
                            connection = druidDataSource.getConnection();
                            connection.setAutoCommit(true);
                            PreparedStatement preparedStatement = connection.prepareStatement("SELECT id,name,state,retry,dt,ct FROM t_tcc");
                            ResultSet rs = preparedStatement.executeQuery();
                            while (rs.next()) {
                                String id = rs.getString("id");
                                String name = rs.getString("name");
                                String state = rs.getString("state");
                                long retry = rs.getLong("retry");
                                long dt = rs.getLong("dt");
                                long ct = rs.getLong("ct");
                                myTccList.add(new MyTcc(id, name, MyTcc.State.valueOf(state), retry, dt, ct));
                            }
                            // connection.commit();
                        } catch (Exception e) {
                            if (connection != null) {
                                try {
                                    connection.rollback();
                                } catch (Exception ignore) {
                                    // none
                                }
                            }
                            throw new TccException(e);
                        } finally {
                            try {
                                if (connection != null) {
                                    connection.setAutoCommit(true);
                                    connection.close();
                                }
                            } catch (Exception ignore) {
                                // none
                            }
                        }
                    }
                    // 超时检测
                    {
                        for (MyTcc myTcc : myTccList) {
                            try {
                                // 超时->修改状态
                                if (myTcc.getState() == MyTcc.State.TRY
                                        && myTcc.getDt() > 0
                                        && myTcc.getDt() < System.currentTimeMillis()) {
                                    {
                                        Connection connection = null;
                                        try {
                                            connection = druidDataSource.getConnection();
                                            connection.setAutoCommit(true);
                                            PreparedStatement preparedStatement = connection.prepareStatement("UPDATE t_tcc SET state = ? , dt = ? WHERE id = ? AND state = ?");
                                            String[] params = new String[]{MyTcc.State.CANCEL.name(), String.valueOf(0), myTcc.getId(), MyTcc.State.TRY.name()};
                                            for (int i = 0; i < params.length; ++i) {
                                                preparedStatement.setString(i + 1, params[i]);
                                            }
                                            int line = preparedStatement.executeUpdate();
                                            if (line <= 0) {
                                                throw new RuntimeException("无法修改状态");
                                            }
                                            // 修改状态
                                            myTcc.setState(MyTcc.State.CANCEL);
                                        } catch (Exception e) {
                                            if (connection != null) {
                                                try {
                                                    connection.rollback();
                                                } catch (Exception ignore) {
                                                    // none
                                                }
                                            }
                                            throw new TccException(e);
                                        } finally {
                                            try {
                                                if (connection != null) {
                                                    connection.setAutoCommit(true);
                                                    connection.close();
                                                }
                                            } catch (Exception ignore) {
                                                // none
                                            }
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                logger.error(e.getMessage(), e);
                            }

                        }
                    }
                    // 重试 CONFIRM CANCEL
                    {
                        for (MyTcc myTcc : myTccList) {
                            try {
                                // 至少需要1分钟后，才能进行重试
                                if (System.currentTimeMillis() - myTcc.getCt() < 1000 * 60) {
                                    continue;
                                }
                                // 需要CONFIRM或者CANCEL状态
                                if (myTcc.getState() != MyTcc.State.CONFIRM
                                        && myTcc.getState() != MyTcc.State.CANCEL) {
                                    continue;
                                }
                                // 重试的次数过多，则放弃
                                if (myTcc.getRetry() > 5) {
                                    continue;
                                }
                                // 增加重试计数
                                {
                                    Connection connection = null;
                                    try {
                                        connection = druidDataSource.getConnection();
                                        connection.setAutoCommit(true);
                                        PreparedStatement preparedStatement = connection.prepareStatement("UPDATE t_tcc SET retry = retry + 1 WHERE id = ?");
                                        String[] params = new String[]{myTcc.getId()};
                                        for (int i = 0; i < params.length; ++i) {
                                            preparedStatement.setString(i + 1, params[i]);
                                        }
                                        preparedStatement.execute();
                                    } catch (Exception e) {
                                        if (connection != null) {
                                            try {
                                                connection.rollback();
                                            } catch (Exception ignore) {
                                                // none
                                            }
                                        }
                                        throw new TccException(e);
                                    } finally {
                                        try {
                                            if (connection != null) {
                                                connection.setAutoCommit(true);
                                                connection.close();
                                            }
                                        } catch (Exception ignore) {
                                            // none
                                        }
                                    }
                                }
                                // 重试
                                {
                                    if (myTcc.getState() == MyTcc.State.CONFIRM) {
                                        confirm0(myTcc.getId());
                                    } else if (myTcc.getState() == MyTcc.State.CANCEL) {
                                        cancel0(myTcc.getId());
                                    }
                                }
                            } catch (Exception e) {
                                logger.error(e.getMessage(), e);
                            }
                        }
                    }
                } catch (Throwable e) {
                    logger.error(e.getMessage(), e);
                }
            }
        }, 16, 16, TimeUnit.SECONDS);
    }

    public String start(String tccName) throws TccException {
        Connection connection = null;
        try {
            MyTcc myTcc = new MyTcc(UUID.randomUUID().toString(), tccName, MyTcc.State.TRY, 0, System.currentTimeMillis() + 1000 * 60 * 5, System.currentTimeMillis());
            connection = druidDataSource.getConnection();
            connection.setAutoCommit(false);
            PreparedStatement preparedStatement = connection.prepareStatement("INSERT INTO t_tcc(id,name,state,retry,dt,ct) VALUES(?,?,?,?,?,?)");
            String[] params = new String[]{myTcc.getId(), myTcc.getName(), myTcc.getState().name(), String.valueOf(myTcc.getRetry()), String.valueOf(myTcc.getDt()), String.valueOf(myTcc.getCt())};
            for (int i = 0; i < params.length; ++i) {
                preparedStatement.setString(i + 1, params[i]);
            }
            preparedStatement.execute();
            connection.commit();
            return myTcc.getId();
        } catch (Exception e) {
            if (connection != null) {
                try {
                    connection.rollback();
                } catch (Exception ignore) {
                    // none
                }
            }
            throw new TccException(e);
        } finally {
            try {
                if (connection != null) {
                    connection.setAutoCommit(true);
                    connection.close();
                }
            } catch (Exception ignore) {
                // none
            }
        }
    }

    public String exec(String tccId, String tccTransactionName, String tccArgs) throws TccException {
        // 登记
        String txId = null;
        {
            Connection connection = null;
            try {
                MyTccTx myTccTx = new MyTccTx(UUID.randomUUID().toString(), tccId, tccTransactionName,
                        tccArgs, "", "", MyTccTx.State.TRY_TODO, System.currentTimeMillis());
                connection = druidDataSource.getConnection();
                connection.setAutoCommit(false);
                PreparedStatement preparedStatement = connection.prepareStatement("INSERT INTO t_tcc_tx(id,tcc_id,tx_name,tx_param,tx_ret,tx_throw,state,ct) VALUES(?,?,?,?,?,?,?,?)");
                String[] params = new String[]{myTccTx.getId(), myTccTx.getTccId(), myTccTx.getTxName(), myTccTx.getTxParam(), myTccTx.getTxRet(), myTccTx.getTxThrow(), myTccTx.getState().name(), String.valueOf(myTccTx.getCt())};
                for (int i = 0; i < params.length; ++i) {
                    preparedStatement.setString(i + 1, params[i]);
                }
                preparedStatement.execute();
                connection.commit();
                txId = myTccTx.getId();
            } catch (Exception e) {
                if (connection != null) {
                    try {
                        connection.rollback();
                    } catch (Exception ignore) {
                        // none
                    }
                }
                throw new TccException(e);
            } finally {
                try {
                    if (connection != null) {
                        connection.setAutoCommit(true);
                        connection.close();
                    }
                } catch (Exception ignore) {
                    // none
                }
            }
        }
        // 执行
        String ret = null;
        String exp = null;
        {

            try {
                TccTransaction tccTransaction = (TccTransaction) Rpc.refer(Class.forName(tccTransactionName));
                ret = tccTransaction.transaction(tccId, TccState.TCC_TRY, tccArgs);
            } catch (Exception e) {
                exp = e.getMessage();
            }
            ret = Strings.nullToEmpty(ret);
            exp = Strings.nullToEmpty(exp);
        }
        // 记录执行结果
        {
            Connection connection = null;
            try {
                connection = druidDataSource.getConnection();
                connection.setAutoCommit(false);
                PreparedStatement preparedStatement = connection.prepareStatement("UPDATE t_tcc_tx SET tx_ret = ? , tx_throw = ?,state = ? WHERE id = ?");
                String[] params = new String[]{ret, exp, MyTccTx.State.TRY_DONE.name(), txId};
                for (int i = 0; i < params.length; ++i) {
                    preparedStatement.setString(i + 1, params[i]);
                }
                preparedStatement.execute();
                connection.commit();
            } catch (Exception e) {
                if (connection != null) {
                    try {
                        connection.rollback();
                    } catch (Exception ignore) {
                        // none
                    }
                }
                throw new TccException(e);
            } finally {
                try {
                    if (connection != null) {
                        connection.setAutoCommit(true);
                        connection.close();
                    }
                } catch (Exception ignore) {
                    // none
                }
            }
        }
        // 返回结果
        {
            if (!"".equals(exp)) {
                throw new TxwException(exp);
            } else {
                return ret;
            }
        }
    }

    public void confirm(String tccId) throws TccException {
        // 状态检测和修改commit状态
        {
            Connection connection = null;
            try {
                connection = druidDataSource.getConnection();
                connection.setAutoCommit(true);
                PreparedStatement preparedStatement = connection.prepareStatement("UPDATE t_tcc SET state = ? , dt = ? WHERE id = ? AND state = ?");
                String[] params = new String[]{MyTcc.State.CONFIRM.name(), String.valueOf(0), tccId, MyTcc.State.TRY.name()};
                for (int i = 0; i < params.length; ++i) {
                    preparedStatement.setString(i + 1, params[i]);
                }
                int line = preparedStatement.executeUpdate();
                if (line <= 0) {
                    throw new TccException("错误的状态修改" + tccId);
                }
                // connection.commit();
            } catch (Exception e) {
                if (connection != null) {
                    try {
                        connection.rollback();
                    } catch (Exception ignore) {
                        // none
                    }
                }
                throw new TccException(e);
            } finally {
                try {
                    if (connection != null) {
                        connection.setAutoCommit(true);
                        connection.close();
                    }
                } catch (Exception ignore) {
                    // none
                }
            }
        }
        try {
            confirm0(tccId);
        } catch (Exception e) {
            // none
        }
    }

    private void confirm0(String tccId) {
        // 真实提交逻辑
        // 状态检测
        {
            Connection connection = null;
            try {
                connection = druidDataSource.getConnection();
                connection.setAutoCommit(true);
                PreparedStatement preparedStatement = connection.prepareStatement("SELECT state FROM t_tcc WHERE id = ?");
                String[] params = new String[]{tccId};
                for (int i = 0; i < params.length; ++i) {
                    preparedStatement.setString(i + 1, params[i]);
                }
                ResultSet rs = preparedStatement.executeQuery();
                rs.next();
                String state = rs.getString(1);
                if (!MyTcc.State.CONFIRM.name().equals(state)) {
                    throw new TccException("错误的状态修改" + tccId);
                }
                // connection.commit();
            } catch (Exception e) {
                if (connection != null) {
                    try {
                        connection.rollback();
                    } catch (Exception ignore) {
                        // none
                    }
                }
                throw new TccException(e);
            } finally {
                try {
                    if (connection != null) {
                        connection.setAutoCommit(true);
                        connection.close();
                    }
                } catch (Exception ignore) {
                    // none
                }
            }
        }
        List<MyTccTx> myTccTxList = new LinkedList<MyTccTx>();
        // 获取提交的tx
        {
            Connection connection = null;
            try {
                connection = druidDataSource.getConnection();
                connection.setAutoCommit(true);
                PreparedStatement preparedStatement = connection.prepareStatement("SELECT id,tx_name,tx_param,state FROM t_tcc_tx WHERE tcc_id = ?");
                String[] params = new String[]{tccId};
                for (int i = 0; i < params.length; ++i) {
                    preparedStatement.setString(i + 1, params[i]);
                }
                ResultSet rs = preparedStatement.executeQuery();
                while (rs.next()) {
                    String id = rs.getString("id");
                    String txName = rs.getString("tx_name");
                    String txParam = rs.getString("tx_param");
                    String state = rs.getString("state");
                    myTccTxList.add(new MyTccTx(id, tccId, txName, txParam, null, null, MyTccTx.State.valueOf(state), 0));
                }
                // connection.commit();
            } catch (Exception e) {
                if (connection != null) {
                    try {
                        connection.rollback();
                    } catch (Exception ignore) {
                        // none
                    }
                }
                throw new TccException(e);
            } finally {
                try {
                    if (connection != null) {
                        connection.setAutoCommit(true);
                        connection.close();
                    }
                } catch (Exception ignore) {
                    // none
                }
            }
        }
        // tx 提交
        {
            for (MyTccTx myTccTx : myTccTxList) {
                try {
                    if (MyTccTx.State.TRY_DONE == myTccTx.getState()
                            || MyTccTx.State.CONFIRM_TODO == myTccTx.getState()) {
                        // 更新状态
                        {
                            Connection connection = null;
                            try {
                                connection = druidDataSource.getConnection();
                                connection.setAutoCommit(true);
                                PreparedStatement preparedStatement = connection.prepareStatement("UPDATE t_tcc_tx SET state = ? WHERE id = ?");
                                String[] params = new String[]{MyTccTx.State.CONFIRM_TODO.name(), myTccTx.getId()};
                                for (int i = 0; i < params.length; ++i) {
                                    preparedStatement.setString(i + 1, params[i]);
                                }
                                preparedStatement.execute();
                                //connection.commit();
                            } catch (Exception e) {
                                if (connection != null) {
                                    try {
                                        connection.rollback();
                                    } catch (Exception ignore) {
                                        // none
                                    }
                                }
                                throw new TccException(e);
                            } finally {
                                try {
                                    if (connection != null) {
                                        connection.setAutoCommit(true);
                                        connection.close();
                                    }
                                } catch (Exception ignore) {
                                    // none
                                }
                            }
                        }
                        // 提交
                        {
                            TccTransaction tccTransaction = (TccTransaction) Rpc.refer(Class.forName(myTccTx.getTxName()));
                            tccTransaction.transaction(tccId, TccState.TCC_CONFIRM, myTccTx.getTxParam());
                        }
                        // 执行成功，登记本次执行的结果
                        {
                            Connection connection = null;
                            try {
                                connection = druidDataSource.getConnection();
                                connection.setAutoCommit(true);
                                PreparedStatement preparedStatement = connection.prepareStatement("UPDATE t_tcc_tx SET state = ? WHERE id = ?");
                                String[] params = new String[]{MyTccTx.State.CONFIRM_DONE.name(), myTccTx.getId()};
                                for (int i = 0; i < params.length; ++i) {
                                    preparedStatement.setString(i + 1, params[i]);
                                }
                                preparedStatement.execute();
                                //connection.commit();
                                // 更新状态
                                myTccTx.setState(MyTccTx.State.CONFIRM_DONE);
                            } catch (Exception e) {
                                if (connection != null) {
                                    try {
                                        connection.rollback();
                                    } catch (Exception ignore) {
                                        // none
                                    }
                                }
                                throw new TccException(e);
                            } finally {
                                try {
                                    if (connection != null) {
                                        connection.setAutoCommit(true);
                                        connection.close();
                                    }
                                } catch (Exception ignore) {
                                    // none
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // none
                }
            }
        }
        // 根据本次结果进行判断，是否完结
        {
            boolean done = true;
            for (MyTccTx myTccTx : myTccTxList) {
                done = (myTccTx.getState() == MyTccTx.State.CONFIRM_DONE);
                if (!done) {
                    break;
                }
            }
            // 完成本次事务的提交
            if (done) {
                {
                    Connection connection = null;
                    try {
                        connection = druidDataSource.getConnection();
                        connection.setAutoCommit(true);
                        PreparedStatement preparedStatement = connection.prepareStatement("UPDATE t_tcc SET state = ? WHERE id = ?");
                        String[] params = new String[]{MyTcc.State.DONE.name(), tccId};
                        for (int i = 0; i < params.length; ++i) {
                            preparedStatement.setString(i + 1, params[i]);
                        }
                        preparedStatement.execute();
                        //connection.commit();
                    } catch (Exception e) {
                        if (connection != null) {
                            try {
                                connection.rollback();
                            } catch (Exception ignore) {
                                // none
                            }
                        }
                        throw new TccException(e);
                    } finally {
                        try {
                            if (connection != null) {
                                connection.setAutoCommit(true);
                                connection.close();
                            }
                        } catch (Exception ignore) {
                            // none
                        }
                    }
                }
            }
        }
    }

    public void cancel(String tccId) throws TccException {
        // 状态检测和修改cancel状态
        {
            Connection connection = null;
            try {
                connection = druidDataSource.getConnection();
                connection.setAutoCommit(true);
                PreparedStatement preparedStatement = connection.prepareStatement("UPDATE t_tcc SET state = ? , dt = ? WHERE id = ? AND state = ?");
                String[] params = new String[]{MyTcc.State.CANCEL.name(), String.valueOf(0), tccId, MyTcc.State.TRY.name()};
                for (int i = 0; i < params.length; ++i) {
                    preparedStatement.setString(i + 1, params[i]);
                }
                int line = preparedStatement.executeUpdate();
                if (line <= 0) {
                    throw new TccException("错误的状态修改" + tccId);
                }
                // connection.commit();
            } catch (Exception e) {
                if (connection != null) {
                    try {
                        connection.rollback();
                    } catch (Exception ignore) {
                        // none
                    }
                }
                throw new TccException(e);
            } finally {
                try {
                    if (connection != null) {
                        connection.setAutoCommit(true);
                        connection.close();
                    }
                } catch (Exception ignore) {
                    // none
                }
            }
        }
        try {
            cancel0(tccId);
        } catch (Exception e) {
            // none
        }
    }

    private void cancel0(String tccId) {
        // 真实提交逻辑
        // 状态检测
        {
            Connection connection = null;
            try {
                connection = druidDataSource.getConnection();
                connection.setAutoCommit(true);
                PreparedStatement preparedStatement = connection.prepareStatement("SELECT state FROM t_tcc WHERE id = ?");
                String[] params = new String[]{tccId};
                for (int i = 0; i < params.length; ++i) {
                    preparedStatement.setString(i + 1, params[i]);
                }
                ResultSet rs = preparedStatement.executeQuery();
                rs.next();
                String state = rs.getString(1);
                if (!MyTcc.State.CANCEL.name().equals(state)) {
                    throw new TccException("错误的状态修改" + tccId);
                }
                // connection.commit();
            } catch (Exception e) {
                if (connection != null) {
                    try {
                        connection.rollback();
                    } catch (Exception ignore) {
                        // none
                    }
                }
                throw new TccException(e);
            } finally {
                try {
                    if (connection != null) {
                        connection.setAutoCommit(true);
                        connection.close();
                    }
                } catch (Exception ignore) {
                    // none
                }
            }
        }
        List<MyTccTx> myTccTxList = new LinkedList<MyTccTx>();
        // 获取提交的tx
        {
            Connection connection = null;
            try {
                connection = druidDataSource.getConnection();
                connection.setAutoCommit(true);
                PreparedStatement preparedStatement = connection.prepareStatement("SELECT id,tx_name,tx_param,state FROM t_tcc_tx WHERE tcc_id = ?");
                String[] params = new String[]{tccId};
                for (int i = 0; i < params.length; ++i) {
                    preparedStatement.setString(i + 1, params[i]);
                }
                ResultSet rs = preparedStatement.executeQuery();
                while (rs.next()) {
                    String id = rs.getString("id");
                    String txName = rs.getString("tx_name");
                    String txParam = rs.getString("tx_param");
                    String state = rs.getString("state");
                    myTccTxList.add(new MyTccTx(id, tccId, txName, txParam, null, null, MyTccTx.State.valueOf(state), 0));
                }
                // connection.commit();
            } catch (Exception e) {
                if (connection != null) {
                    try {
                        connection.rollback();
                    } catch (Exception ignore) {
                        // none
                    }
                }
                throw new TccException(e);
            } finally {
                try {
                    if (connection != null) {
                        connection.setAutoCommit(true);
                        connection.close();
                    }
                } catch (Exception ignore) {
                    // none
                }
            }
        }
        // tx 提交
        {
            for (MyTccTx myTccTx : myTccTxList) {
                try {
                    if (MyTccTx.State.TRY_DONE == myTccTx.getState()
                            || MyTccTx.State.CANCEL_TODO == myTccTx.getState()) {
                        // 更新状态
                        {
                            Connection connection = null;
                            try {
                                connection = druidDataSource.getConnection();
                                connection.setAutoCommit(true);
                                PreparedStatement preparedStatement = connection.prepareStatement("UPDATE t_tcc_tx SET state = ? WHERE id = ?");
                                String[] params = new String[]{MyTccTx.State.CANCEL_TODO.name(), myTccTx.getId()};
                                for (int i = 0; i < params.length; ++i) {
                                    preparedStatement.setString(i + 1, params[i]);
                                }
                                preparedStatement.execute();
                                //connection.commit();
                            } catch (Exception e) {
                                if (connection != null) {
                                    try {
                                        connection.rollback();
                                    } catch (Exception ignore) {
                                        // none
                                    }
                                }
                                throw new TccException(e);
                            } finally {
                                try {
                                    if (connection != null) {
                                        connection.setAutoCommit(true);
                                        connection.close();
                                    }
                                } catch (Exception ignore) {
                                    // none
                                }
                            }
                        }
                        // 提交
                        {
                            TccTransaction tccTransaction = (TccTransaction) Rpc.refer(Class.forName(myTccTx.getTxName()));
                            tccTransaction.transaction(tccId, TccState.TCC_CANCEL, myTccTx.getTxParam());
                        }
                        // 执行成功，登记本次执行的结果
                        {
                            Connection connection = null;
                            try {
                                connection = druidDataSource.getConnection();
                                connection.setAutoCommit(true);
                                PreparedStatement preparedStatement = connection.prepareStatement("UPDATE t_tcc_tx SET state = ? WHERE id = ?");
                                String[] params = new String[]{MyTccTx.State.CANCEL_DONE.name(), myTccTx.getId()};
                                for (int i = 0; i < params.length; ++i) {
                                    preparedStatement.setString(i + 1, params[i]);
                                }
                                preparedStatement.execute();
                                //connection.commit();
                                // 更新状态
                                myTccTx.setState(MyTccTx.State.CANCEL_DONE);
                            } catch (Exception e) {
                                if (connection != null) {
                                    try {
                                        connection.rollback();
                                    } catch (Exception ignore) {
                                        // none
                                    }
                                }
                                throw new TccException(e);
                            } finally {
                                try {
                                    if (connection != null) {
                                        connection.setAutoCommit(true);
                                        connection.close();
                                    }
                                } catch (Exception ignore) {
                                    // none
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // none
                }
            }
        }
        // 根据本次结果进行判断，是否完结
        {
            boolean done = true;
            for (MyTccTx myTccTx : myTccTxList) {
                done = (myTccTx.getState() == MyTccTx.State.CANCEL_DONE);
                if (!done) {
                    break;
                }
            }
            // 完成本次事务的提交
            if (done) {
                {
                    Connection connection = null;
                    try {
                        connection = druidDataSource.getConnection();
                        connection.setAutoCommit(true);
                        PreparedStatement preparedStatement = connection.prepareStatement("UPDATE t_tcc SET state = ? WHERE id = ?");
                        String[] params = new String[]{MyTcc.State.DONE.name(), tccId};
                        for (int i = 0; i < params.length; ++i) {
                            preparedStatement.setString(i + 1, params[i]);
                        }
                        preparedStatement.execute();
                        //connection.commit();
                    } catch (Exception e) {
                        if (connection != null) {
                            try {
                                connection.rollback();
                            } catch (Exception ignore) {
                                // none
                            }
                        }
                        throw new TccException(e);
                    } finally {
                        try {
                            if (connection != null) {
                                connection.setAutoCommit(true);
                                connection.close();
                            }
                        } catch (Exception ignore) {
                            // none
                        }
                    }
                }
            }
        }
    }
}
