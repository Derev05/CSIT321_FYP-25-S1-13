package com.example.bioauth;

import org.opencv.core.Rect;

import java.io.Serializable;

public class SerializableRect implements Serializable {
    private int x, y, width, height;

    public SerializableRect(Rect rect) {
        this.x = rect.x;
        this.y = rect.y;
        this.width = rect.width;
        this.height = rect.height;
    }

    public Rect toRect() {
        return new Rect(x, y, width, height);
    }
}
