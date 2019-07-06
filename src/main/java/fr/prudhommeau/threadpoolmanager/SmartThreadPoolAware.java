package fr.prudhommeau.threadpoolmanager;

import java.util.List;

public interface SmartThreadPoolAware {

    List<SmartThreadPool> provideSmartThreadPoolList();

}
