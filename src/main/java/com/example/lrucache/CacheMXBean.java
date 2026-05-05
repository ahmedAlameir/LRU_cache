package com.example.lrucache;

public interface CacheMXBean {
    long getHits();

    long getMisses();

    long getEvictions();

    int getSize();

    double getHitRatio();

}
