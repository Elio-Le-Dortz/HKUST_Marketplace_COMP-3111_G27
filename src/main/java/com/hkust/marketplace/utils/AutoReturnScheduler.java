package com.hkust.marketplace.utils;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Background scheduler that periodically triggers auto-return processing for expired loans
 * (US-B07 Task 1.6 / UC 1.6 Alternative Flow A4).
 */
public class AutoReturnScheduler {

    private static final AutoReturnScheduler INSTANCE = new AutoReturnScheduler();

    private final DummyDataService dataService;
    private ScheduledExecutorService executor;
    private boolean running = false;

    /**
     * Default constructor using the shared {@link DummyDataService} singleton.
     */
    public AutoReturnScheduler() {
        this(DummyDataService.getInstance());
    }

    /**
     * Constructor allowing injection of a specific {@link DummyDataService} instance for testing.
     *
     * @param dataService the data service instance to invoke for auto-returns
     */
    public AutoReturnScheduler(DummyDataService dataService) {
        this.dataService = dataService;
    }

    /**
     * Returns the singleton instance of the scheduler.
     *
     * @return the singleton scheduler instance
     */
    public static AutoReturnScheduler getInstance() {
        return INSTANCE;
    }

    /**
     * Starts the periodic auto-return check at the default 1-second interval using daemon threads.
     */
    public synchronized void start() {
        start(1, TimeUnit.SECONDS);
    }

    /**
     * Starts the periodic auto-return check at the specified interval using daemon threads.
     *
     * @param period the period between successive executions
     * @param unit the time unit of the period parameter
     */
    public synchronized void start(long period, TimeUnit unit) {
        if (running && executor != null && !executor.isShutdown()) {
            return;
        }

        ThreadFactory daemonThreadFactory = r -> {
            Thread t = new Thread(r, "auto-return-scheduler");
            t.setDaemon(true);
            return t;
        };

        executor = Executors.newSingleThreadScheduledExecutor(daemonThreadFactory);
        executor.scheduleAtFixedRate(() -> {
            try {
                if (dataService != null) {
                    dataService.processAutoReturns();
                }
            } catch (Throwable t) {
                // Prevent scheduler cancellation on unexpected errors
            }
        }, period, period, unit);

        running = true;
    }

    /**
     * Stops the background scheduler and terminates its executor.
     */
    public synchronized void stop() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
        running = false;
    }

    /**
     * Tells whether the scheduler is currently active.
     *
     * @return {@code true} if running, {@code false} otherwise
     */
    public synchronized boolean isRunning() {
        return running && executor != null && !executor.isShutdown();
    }
}
