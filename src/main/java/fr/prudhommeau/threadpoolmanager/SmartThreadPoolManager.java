package fr.prudhommeau.threadpoolmanager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class SmartThreadPoolManager {

    private static final int DEFAULT_PRIORITY = 0;

    private static SmartThreadPoolManager instance;
    private final List<SmartThreadPoolListEmptyListenerStructure> smartThreadPoolListEmptyListenerList = Collections.synchronizedList(new ArrayList<>());
    private final List<SmartThreadPoolListFinishedListenerStructure> smartThreadPoolListFinishedListenerList = Collections.synchronizedList(new ArrayList<>());

    private SmartThreadPoolManager() {}

    public interface SmartThreadPoolListEmptyListener {

        void apply();

    }

    public interface SmartThreadPoolListFinishedListener {

        void apply();

    }
    public static class SmartThreadPoolListEmptyListenerStructure {

        private List<SmartThreadPool> smartThreadPoolList;
        private SmartThreadPoolListEmptyListener smartThreadPoolListEmptyListener;
        private int priority;

        public List<SmartThreadPool> getSmartThreadPoolList() {
            return smartThreadPoolList;
        }

        public void setSmartThreadPoolList(List<SmartThreadPool> smartThreadPoolList) {
            this.smartThreadPoolList = smartThreadPoolList;
        }

        public SmartThreadPoolListEmptyListener getSmartThreadPoolListEmptyListener() {
            return smartThreadPoolListEmptyListener;
        }

        public void setSmartThreadPoolListEmptyListener(SmartThreadPoolListEmptyListener smartThreadPoolListEmptyListener) {
            this.smartThreadPoolListEmptyListener = smartThreadPoolListEmptyListener;
        }

        public int getPriority() {
            return priority;
        }

        public void setPriority(int priority) {
            this.priority = priority;
        }

    }

    public static class SmartThreadPoolListFinishedListenerStructure {

        private List<SmartThreadPool> smartThreadPoolList;
        private SmartThreadPoolListFinishedListener smartThreadPoolListFinishedListener;
        private int priority;

        public List<SmartThreadPool> getSmartThreadPoolList() {
            return smartThreadPoolList;
        }

        public void setSmartThreadPoolList(List<SmartThreadPool> smartThreadPoolList) {
            this.smartThreadPoolList = smartThreadPoolList;
        }

        public SmartThreadPoolListFinishedListener getSmartThreadPoolListFinishedListener() {
            return smartThreadPoolListFinishedListener;
        }

        public void setSmartThreadPoolListFinishedListener(SmartThreadPoolListFinishedListener smartThreadPoolListFinishedListener) {
            this.smartThreadPoolListFinishedListener = smartThreadPoolListFinishedListener;
        }

        public int getPriority() {
            return priority;
        }

        public void setPriority(int priority) {
            this.priority = priority;
        }

    }

    public static SmartThreadPoolManager getInstance() {
        if (instance == null) {
            instance = new SmartThreadPoolManager();
        }
        return instance;
    }

    public void registerSmartThreadPoolListEmptyListener(List<SmartThreadPool> smartThreadPoolList, SmartThreadPoolListEmptyListener listener) {
        registerSmartThreadPoolListEmptyListener(smartThreadPoolList, DEFAULT_PRIORITY, listener);
    }

    public void registerSmartThreadPoolListEmptyListener(List<SmartThreadPool> smartThreadPoolList, int priority, SmartThreadPoolListEmptyListener listener) {
        SmartThreadPoolListEmptyListenerStructure smartThreadPoolListEmptyListenerStructure = new SmartThreadPoolListEmptyListenerStructure();
        smartThreadPoolListEmptyListenerStructure.setSmartThreadPoolList(smartThreadPoolList);
        smartThreadPoolListEmptyListenerStructure.setPriority(priority);
        smartThreadPoolListEmptyListenerStructure.setSmartThreadPoolListEmptyListener(listener);
        synchronized (smartThreadPoolListEmptyListenerList) {
            smartThreadPoolListEmptyListenerList.add(smartThreadPoolListEmptyListenerStructure);
            smartThreadPoolListEmptyListenerList.sort(Comparator.comparingInt(SmartThreadPoolListEmptyListenerStructure::getPriority));
        }
    }

    public void registerSmartThreadPoolListFinishedListener(List<SmartThreadPool> smartThreadPoolList, SmartThreadPoolListFinishedListener listener) {
        registerSmartThreadPoolListFinishedListener(smartThreadPoolList, DEFAULT_PRIORITY, listener);
    }

    public void registerSmartThreadPoolListFinishedListener(List<SmartThreadPool> smartThreadPoolList, int priority, SmartThreadPoolListFinishedListener listener) {
        SmartThreadPoolListFinishedListenerStructure smartThreadPoolListFinishedListenerStructure = new SmartThreadPoolListFinishedListenerStructure();
        smartThreadPoolListFinishedListenerStructure.setSmartThreadPoolList(smartThreadPoolList);
        smartThreadPoolListFinishedListenerStructure.setPriority(priority);
        smartThreadPoolListFinishedListenerStructure.setSmartThreadPoolListFinishedListener(listener);
        synchronized (smartThreadPoolListFinishedListenerList) {
            smartThreadPoolListFinishedListenerList.add(smartThreadPoolListFinishedListenerStructure);
            smartThreadPoolListFinishedListenerList.sort(Comparator.comparingInt(SmartThreadPoolListFinishedListenerStructure::getPriority));
        }
    }

    public void addSmartThreadPool(SmartThreadPool smartThreadPool) {
        smartThreadPool.registerThreadPoolEmptyEventListener(() -> {
            synchronized (smartThreadPoolListEmptyListenerList) {
                List<SmartThreadPoolListEmptyListenerStructure> smartThreadPoolListEmptyListenerStructureToRemove = new ArrayList<>();
                for (SmartThreadPoolListEmptyListenerStructure smartThreadPoolListEmptyListenerStructure : smartThreadPoolListEmptyListenerList) {
                    List<SmartThreadPool> eligibleSmartThreadPoolList = smartThreadPoolListEmptyListenerStructure.getSmartThreadPoolList();
                    if (eligibleSmartThreadPoolList.stream().allMatch(smartThreadPool1 -> smartThreadPool1.getRunningInstances().size() == 0 && smartThreadPool1.getQueuedInstances().size() == 0)) {
                        CompletableFuture.runAsync(() -> smartThreadPoolListEmptyListenerStructure.getSmartThreadPoolListEmptyListener().apply());
                        smartThreadPoolListEmptyListenerStructureToRemove.add(smartThreadPoolListEmptyListenerStructure);
                    }
                }
                smartThreadPoolListEmptyListenerStructureToRemove.forEach(smartThreadPoolListEmptyListenerList::remove);
            }
        });
        smartThreadPool.registerThreadPoolFinishedEventListener(() -> {
            synchronized (smartThreadPoolListFinishedListenerList) {
                List<SmartThreadPoolListFinishedListenerStructure> smartThreadPoolListFinishedListenerStructureToRemove = new ArrayList<>();
                for (SmartThreadPoolListFinishedListenerStructure smartThreadPoolListFinishedListenerStructure : smartThreadPoolListFinishedListenerList) {
                    List<SmartThreadPool> eligibleSmartThreadPoolList = smartThreadPoolListFinishedListenerStructure.getSmartThreadPoolList();
                    if (eligibleSmartThreadPoolList.stream().allMatch(SmartThreadPool::isClosed)) {
                        CompletableFuture.runAsync(() -> smartThreadPoolListFinishedListenerStructure.getSmartThreadPoolListFinishedListener().apply());
                        smartThreadPoolListFinishedListenerStructureToRemove.add(smartThreadPoolListFinishedListenerStructure);
                    }
                }
                smartThreadPoolListFinishedListenerStructureToRemove.forEach(smartThreadPoolListFinishedListenerList::remove);
            }
        });
    }

}
