package fr.prudhommeau.threadpoolmanager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SmartThread extends Thread implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(SmartThread.class);

    protected final UUID uuid = UUID.randomUUID();
    public boolean responded = false;
    protected Map<String, Object> metadata = new HashMap<>();
    protected SmartThreadPool threadPool;
    private OnThreadRunningListener listener;
    private boolean correctlyLaunched = false;

    public SmartThread() {
        setName("SmartThread-" + uuid);
    }

    private SmartThread(SmartThread smartThread) {
        super();
        this.metadata = smartThread.metadata;
        this.threadPool = smartThread.threadPool;
        this.listener = smartThread.listener;
        this.correctlyLaunched = smartThread.correctlyLaunched;
    }

    public interface OnThreadRunningListener {
        void onThreadRunning(SmartThread smartThreadInstance, Map<String, Object> metadata) throws IOException;
    }

    public void launchRequest() {
        correctlyLaunched = true;
        this.setUncaughtExceptionHandler(threadPool.getUncaughtExceptionHandler());
        this.threadPool.addToQueue(this);
    }

    @Override
    public void run() {
        if (correctlyLaunched) {
            try {
                listener.onThreadRunning(this, metadata);
            } catch (Throwable t) {
                threadPool.abnormallyCloseSmartThread(this, t);
            }
            threadPool.closeSmartThread(this);
        } else {
            logger.error("This thread must be launched with launchRequest()");
        }
    }

    public void retryWithAnotherThread() {
        SmartThread smartThread = new SmartThread(this);
        smartThread.launchRequest();
    }

    public void addMetadata(String key, Object value) {
        metadata.put(key, value);
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public void setListener(OnThreadRunningListener listener) {
        this.listener = listener;
    }

    public void setThreadPool(SmartThreadPool threadPool) {
        this.threadPool = threadPool;
    }

    public UUID getUuid() {
        return uuid;
    }

}
