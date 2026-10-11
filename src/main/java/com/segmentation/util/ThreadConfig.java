package com.segmentation.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Owns the application's configurable ForkJoinPool for parallel operations.
 */
public final class ThreadConfig {

    private static final int AVAILABLE_PROCESSORS =
            Math.max(1, Runtime.getRuntime().availableProcessors());
    private static volatile int configuredThreadCount = AVAILABLE_PROCESSORS;
    private static volatile ForkJoinPool pool = new ForkJoinPool(configuredThreadCount);

    private ThreadConfig() {}

    public static int getAvailableProcessors() {
        return AVAILABLE_PROCESSORS;
    }

    public static int getThreadCount() {
        return configuredThreadCount;
    }

    public static int getPoolParallelism() {
        return pool.getParallelism();
    }

    public static boolean isPoolShutdown() {
        return pool.isShutdown();
    }

    public static synchronized void setThreadCount(int threadCount) {
        if (threadCount <= 0 || threadCount > 32767) {
            throw new IllegalArgumentException("Thread count must be between 1 and 32767.");
        }
        if (threadCount == configuredThreadCount) {
            return;
        }

        ForkJoinPool previousPool = pool;
        pool = new ForkJoinPool(threadCount);
        configuredThreadCount = threadCount;
        shutdownPool(previousPool);
    }

    public static <T> T runInPool(Callable<T> task) {
        ForkJoinPool activePool = pool;
        if (ForkJoinTask.getPool() == activePool) {
            return call(task);
        }
        ForkJoinTask<T> submitted = activePool.submit(task);
        try {
            return submitted.get();
        } catch (InterruptedException e) {
            submitted.cancel(true);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for configured worker pool.", e);
        } catch (ExecutionException e) {
            throw rethrowTaskFailure(e.getCause());
        }
    }

    public static <T, R> List<R> parallelMap(List<T> values, Function<? super T, ? extends R> mapper) {
        if (configuredThreadCount == 1 || values.size() < 2) {
            List<R> results = new ArrayList<>(values.size());
            for (T value : values) {
                results.add(mapper.apply(value));
            }
            return results;
        }
        return runInPool(() -> values.parallelStream()
                .map(mapper)
                .collect(Collectors.toList()));
    }

    public static synchronized void shutdown() {
        shutdownPool(pool);
    }

    private static <T> T call(Callable<T> task) {
        try {
            return task.call();
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Configured worker task failed.", e);
        }
    }

    private static RuntimeException rethrowTaskFailure(Throwable cause) {
        if (cause instanceof RuntimeException) {
            return (RuntimeException) cause;
        }
        if (cause instanceof Error) {
            throw (Error) cause;
        }
        return new IllegalStateException("Configured worker task failed.", cause);
    }

    private static void shutdownPool(ForkJoinPool oldPool) {
        oldPool.shutdown();
        try {
            if (!oldPool.awaitTermination(30, TimeUnit.SECONDS)) {
                oldPool.shutdownNow();
                oldPool.awaitTermination(5, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            oldPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
