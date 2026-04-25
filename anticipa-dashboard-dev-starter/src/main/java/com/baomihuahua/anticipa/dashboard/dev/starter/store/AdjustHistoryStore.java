package com.baomihuahua.anticipa.dashboard.dev.starter.store;

import com.baomihuahua.anticipa.dashboard.dev.starter.dto.AdjustRecordDTO;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AdjustHistoryStore {
    private final ConcurrentHashMap<String, CircularBuffer<AdjustRecordDTO>> store = new ConcurrentHashMap<>();
    private static final int DEFAULT_MAX_SIZE = 100;

    public void record(String poolId, AdjustRecordDTO record) {
        CircularBuffer<AdjustRecordDTO> buffer = store.computeIfAbsent(poolId, k -> new CircularBuffer<>(DEFAULT_MAX_SIZE));
        buffer.add(record);
    }

    public List<AdjustRecordDTO> getHistory(String poolId, int limit) {
        CircularBuffer<AdjustRecordDTO> buffer = store.get(poolId);
        if (buffer == null) return Collections.emptyList();
        List<AdjustRecordDTO> all = buffer.stream().collect(Collectors.toList());
        return all.subList(Math.max(0, all.size() - limit), all.size());
    }

    public AdjustRecordDTO getSnapshot(String poolId, String snapshotId) {
        CircularBuffer<AdjustRecordDTO> buffer = store.get(poolId);
        if (buffer == null) return null;
        return buffer.stream().filter(r -> r.getSnapshotId().equals(snapshotId)).findFirst().orElse(null);
    }

    public static class CircularBuffer<T> {
        private final T[] buffer;
        private int index = 0;
        private int size = 0;

        @SuppressWarnings("unchecked")
        public CircularBuffer(int maxSize) {
            this.buffer = (T[]) new Object[maxSize];
        }

        public synchronized void add(T item) {
            buffer[index] = item;
            index = (index + 1) % buffer.length;
            if (size < buffer.length) size++;
        }

        public synchronized Stream<T> stream() {
            List<T> list = new ArrayList<>(size);
            if (size < buffer.length) {
                for (int i = 0; i < size; i++) list.add(buffer[i]);
            } else {
                for (int i = 0; i < buffer.length; i++) {
                    list.add(buffer[(index + i) % buffer.length]);
                }
            }
            return list.stream();
        }
    }
}
