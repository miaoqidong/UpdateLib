package com.mqd.updatejava.download;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 下载状态控制器（内存单例，替换 Kotlin StateFlow）。
 */
public class DownloadController {

    public enum Status { IDLE, DOWNLOADING, FAILED }

    public static class State {
        public Status status = Status.IDLE;
        public String version = "";
        public int progress = 0;
    }

    public interface Listener {
        void onStateChanged(State state);
    }

    private static final State currentState = new State();
    private static final List<Listener> listeners = new CopyOnWriteArrayList<>();

    public static void addListener(Listener listener) {
        if (listener != null) listeners.add(listener);
    }

    public static void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public static State getCurrentState() {
        return currentState;
    }

    public static void onStart(String version) {
        synchronized (currentState) {
            currentState.status = Status.DOWNLOADING;
            currentState.version = version;
            currentState.progress = 0;
        }
        notifyListeners();
    }

    public static void onProgress(int percent) {
        synchronized (currentState) {
            currentState.progress = Math.max(0, Math.min(100, percent));
        }
        notifyListeners();
    }

    public static void onFinish() {
        synchronized (currentState) {
            currentState.status = Status.IDLE;
            currentState.version = "";
            currentState.progress = 0;
        }
        notifyListeners();
    }

    public static void onFailed(String version) {
        synchronized (currentState) {
            currentState.status = Status.FAILED;
            currentState.version = version;
        }
        notifyListeners();
    }

    public static void reset() {
        synchronized (currentState) {
            currentState.status = Status.IDLE;
            currentState.version = "";
            currentState.progress = 0;
        }
        notifyListeners();
    }

    private static void notifyListeners() {
        State snapshot;
        synchronized (currentState) {
            snapshot = new State();
            snapshot.status = currentState.status;
            snapshot.version = currentState.version;
            snapshot.progress = currentState.progress;
        }
        for (Listener listener : listeners) {
            listener.onStateChanged(snapshot);
        }
    }
}
