package com.dasi.domain.xhspublish.service.support;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

@Component
public class XhsPublishTaskLockSupport {

    private final ConcurrentHashMap<String, ReentrantLock> lockMap = new ConcurrentHashMap<>();

    public <T> T withTaskLock(String taskId, Supplier<T> supplier) {
        ReentrantLock lock = lockMap.computeIfAbsent(taskId, key -> new ReentrantLock());
        lock.lock();
        try {
            return supplier.get();
        } finally {
            lock.unlock();
            if (!lock.isLocked() && !lock.hasQueuedThreads()) {
                lockMap.remove(taskId, lock);
            }
        }
    }

    public void withTaskLock(String taskId, Runnable runnable) {
        withTaskLock(taskId, () -> {
            runnable.run();
            return null;
        });
    }

}

