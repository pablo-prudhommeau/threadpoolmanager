package fr.prudhommeau.threadpoolmanager;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

public class SmartThreadPool {

    private static final Logger logger = LoggerFactory.getLogger(SmartThreadPool.class);
    private static final String SMART_THREAD_POOL_FINISHER_THREAD_NAME = "SmartThreadPoolFinisher";

    private final ReentrantLock lock = new ReentrantLock();
    private final List<SmartThread> queuedInstances = Collections.synchronizedList(new ArrayList<>());
    private final List<SmartThread> runningInstances = Collections.synchronizedList(new ArrayList<>());
    private final List<ThreadPoolEmptyEventListener> threadPoolEmptyEventListenerList = Collections.synchronizedList(new ArrayList<>());
    private final List<ThreadPoolFinishedEventListener> threadPoolFinishedEventListenerList = Collections.synchronizedList(new ArrayList<>());
    private final UUID uuid = UUID.randomUUID();

    private Object initiator;
    private Thread.UncaughtExceptionHandler uncaughtExceptionHandler;
    private long numberOfSmartThreadProcessed = 0;
    private long numberOfSmartThreadInterrupted = 0;
    private int runningThreadPoolMaxSize = 200;
    private boolean autoClose = true;
    private boolean interrupted = false;
    private boolean started = false;

    public SmartThreadPool() {
        SmartThreadPoolManager.getInstance().addSmartThreadPool(this);
        Thread smartThreadPoolFinisherThread = new Thread(() -> {
            while (!interrupted) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            threadPoolFinishedEventListenerList.forEach(ThreadPoolFinishedEventListener::apply);
            threadPoolFinishedEventListenerList.clear();
            logger.debug("Smart thread pool has been closed - numberOfSmartThreadProcessed [{}], numberOfSmartThreadInterrupted [{}], smartThreadPool [{}]", numberOfSmartThreadProcessed, numberOfSmartThreadInterrupted, this);
        });
        smartThreadPoolFinisherThread.setName(SMART_THREAD_POOL_FINISHER_THREAD_NAME);
        smartThreadPoolFinisherThread.start();
    }

    public interface ThreadPoolEmptyEventListener {
        void apply();
    }

    public interface ThreadPoolFinishedEventListener {
        void apply();
    }

    public void interruptSmartThread(SmartThread smartThread) {
        if (smartThread.isInterrupted()) {
            logger.debug("Smart thread has already been interrupted - smartThread [{}]", smartThread);
            return;
        }
        lock.lock();
        try {
            smartThread.interrupt();
            numberOfSmartThreadInterrupted++;
            runningInstances.remove(smartThread);
            queuedInstances.remove(smartThread);
        } finally {
            lock.unlock();
        }
        checkSmartThreadPoolState();
    }

    public void closeSmartThread(SmartThread smartThread) {
        if (smartThread.isInterrupted()) {
            logger.trace("Skipping closing smart thread which has already been interrupted - smartThread [{}], smartThreadPool [{}]", smartThread, this);
            return;
        }
        lock.lock();
        try {
            numberOfSmartThreadProcessed++;
            runningInstances.remove(smartThread);
            queuedInstances.remove(smartThread);
        } finally {
            lock.unlock();
        }
        checkSmartThreadPoolState();
    }

    public void abnormallyCloseSmartThread(SmartThread smartThread, Throwable t) {
        closeSmartThread(smartThread);
        throw new RuntimeException(t);
    }

    public void addToQueue(SmartThread smartThread) {
        if (interrupted) {
            logger.debug("Skipping adding to queue smart thread because smart thread pool is interrupted - smartThread [{}], smartThreadPool [{}]", smartThread, this);
            return;
        }
        lock.lock();
        try {
            if (!started || runningInstances.size() >= runningThreadPoolMaxSize) {
                queuedInstances.add(smartThread);
            } else {
                runningInstances.add(smartThread);
                smartThread.start();
                checkSmartThreadPoolState();
            }
        } finally {
            lock.unlock();
        }
        if (!started) {
            start();
        }
    }

    public void start() {
        if (started) {
            logger.error("Cannot start smart thread pool because it has already been started - smartThreadPool [{}]", this);
            throw new RuntimeException();
        }
        started = true;
        checkSmartThreadPoolState();
    }

    public void interrupt() {
        logger.debug("Interrupting smart thread pool - smartThreadPool [{}]", this);
        lock.lock();
        try {
            interrupted = true;
            List<SmartThread> smartThreadList = new ArrayList<>(queuedInstances);
            smartThreadList.forEach(Thread::interrupt);
            queuedInstances.clear();
            numberOfSmartThreadInterrupted += smartThreadList.size();
        } finally {
            lock.unlock();
        }
        checkSmartThreadPoolState();
    }

    public void close() {
        logger.debug("Closing smart thread pool - smartThreadPool [{}]", this);
        if (runningInstances.size() != 0 || queuedInstances.size() != 0) {
            logger.warn("Cannot close smart thread pool because it remains running or queued instances - smartThreadPool [{}]", this);
            return;
        }
        interrupted = true;
    }

    public void registerThreadPoolEmptyEventListener(ThreadPoolEmptyEventListener listener) {
        threadPoolEmptyEventListenerList.add(listener);
    }

    public void registerThreadPoolFinishedEventListener(ThreadPoolFinishedEventListener listener) {
        threadPoolFinishedEventListenerList.add(listener);
    }

    private void checkSmartThreadPoolState() {
        boolean hasAliveThread = false;
        lock.lock();
        try {
            if (runningInstances.size() < runningThreadPoolMaxSize && queuedInstances.size() > 0) {
                queuedInstances.size();
                int freeSpace = runningThreadPoolMaxSize - runningInstances.size();
                for (int i = 0; i < freeSpace; i++) {
                    if (queuedInstances.size() > 0) {
                        SmartThread thread = queuedInstances.remove(0);
                        thread.start();
                        runningInstances.add(thread);
                    } else {
                        i = freeSpace;
                    }
                }
            }
            for (SmartThread thread : runningInstances) {
                if (thread.isAlive()) {
                    hasAliveThread = true;
                    break;
                }
            }
        } finally {
            lock.unlock();
        }
        if (!hasAliveThread) {
            logger.debug("Smart thread pool has no more alive thread to be processed - smartThreadPool [{}]", this);
            if (autoClose) {
                logger.debug("Smart thread pool will be interrupted next to auto-close mechanism - smartThreadPool [{}]", this);
                interrupted = true;
            }
            threadPoolEmptyEventListenerList.forEach(ThreadPoolEmptyEventListener::apply);
        }
    }

    public Object getInitiator() {
        return initiator;
    }

    public void setInitiator(Object initiator) {
        this.initiator = initiator;
    }

    public Thread.UncaughtExceptionHandler getUncaughtExceptionHandler() {
        return uncaughtExceptionHandler;
    }

    public List<SmartThread> getRunningInstances() {
        return runningInstances;
    }

    public List<SmartThread> getQueuedInstances() {
        return queuedInstances;
    }

    public long getNumberOfSmartThreadProcessed() {
        return numberOfSmartThreadProcessed;
    }

    public long getNumberOfSmartThreadInterrupted() {
        return numberOfSmartThreadInterrupted;
    }

    public boolean isInterrupted() {
        return interrupted;
    }

    public ReentrantLock getLock() {
        return lock;
    }

    public boolean isAutoClose() {
        return autoClose;
    }

    public void setUncaughtExceptionHandler(Thread.UncaughtExceptionHandler uncaughtExceptionHandler) {
        this.uncaughtExceptionHandler = uncaughtExceptionHandler;
    }

    public void setRunningThreadPoolMaxSize(int runningThreadPoolMaxSize) {
        this.runningThreadPoolMaxSize = runningThreadPoolMaxSize;
    }

    public void setAutoClose(boolean autoClose) {
        this.autoClose = autoClose;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.JSON_STYLE)
                .append("uuid", uuid)
                .append("initiator", initiator)
                .append("numberOfSmartThreadProcessed", numberOfSmartThreadProcessed)
                .append("numberOfSmartThreadInterrupted", numberOfSmartThreadInterrupted)
                .append("runningThreadPoolMaxSize", runningThreadPoolMaxSize)
                .append("interrupted", interrupted)
                .append("started", started)
                .toString();
    }

}
