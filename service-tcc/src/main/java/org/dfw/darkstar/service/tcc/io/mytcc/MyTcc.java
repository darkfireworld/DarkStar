package org.dfw.darkstar.service.tcc.io.mytcc;

/**
 * MyTcc
 */
public class MyTcc {
    String id;
    String name;
    State state;
    long retry;
    long dt;
    long ct;

    public MyTcc() {
    }

    public MyTcc(String id, String name, State state, long retry, long dt, long ct) {
        this.id = id;
        this.name = name;
        this.state = state;
        this.retry = retry;
        this.dt = dt;
        this.ct = ct;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public long getRetry() {
        return retry;
    }

    public void setRetry(long retry) {
        this.retry = retry;
    }

    public long getDt() {
        return dt;
    }

    public void setDt(long dt) {
        this.dt = dt;
    }

    public long getCt() {
        return ct;
    }

    public void setCt(long ct) {
        this.ct = ct;
    }

    public enum State {
        TRY,
        CONFIRM,
        CANCEL,
        DONE
    }
}
