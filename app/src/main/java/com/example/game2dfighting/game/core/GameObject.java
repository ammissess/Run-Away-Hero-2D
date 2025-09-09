package com.example.game2dfighting.game.core;

import android.graphics.Canvas;

public interface GameObject {
    void update();              // cập nhật logic mỗi frame
    void draw(Canvas canvas);   // vẽ lên canvas
}
