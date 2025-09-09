package com.example.game2dfighting.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

public class JoystickView extends View {
    private float centerX, centerY;  // Tâm joystick
    private float baseRadius, hatRadius;
    private float touchX, touchY;    // Vị trí ngón tay
    private boolean isPressed = false;

    private Paint basePaint, hatPaint, borderPaint;
    private JoystickListener listener;

    public interface JoystickListener {
        void onJoystickMoved(float xPercent, float yPercent);
    }

    public JoystickView(Context context, JoystickListener listener) {
        super(context);
        this.listener = listener;

        // Paint cho vòng ngoài (base) với gradient
        basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        basePaint.setStyle(Paint.Style.FILL);
        basePaint.setShadowLayer(10f, 0f, 5f, Color.argb(100, 0, 0, 0)); // Bóng đổ

        // Paint cho nút joystick (hat) với gradient
        hatPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        hatPaint.setStyle(Paint.Style.FILL);
        hatPaint.setShadowLayer(8f, 0f, 3f, Color.argb(80, 0, 0, 0)); // Bóng đổ nhỏ hơn

        // Paint cho viền vòng ngoài
        borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(4f);
        borderPaint.setColor(Color.argb(200, 255, 255, 255)); // Viền trắng
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        centerX = w / 2f;
        centerY = h / 2f;
        baseRadius = Math.min(w, h) / 2.5f;
        hatRadius = Math.min(w, h) / 5f;

        // Cập nhật gradient cho vòng ngoài
        basePaint.setShader(new RadialGradient(
                centerX, centerY, baseRadius,
                Color.argb(200, 120, 144, 156), // Xám xanh nhạt
                Color.argb(200, 55, 71, 79),   // Xám đậm
                Shader.TileMode.CLAMP
        ));

        // Cập nhật gradient cho nút joystick
        hatPaint.setShader(new RadialGradient(
                centerX, centerY, hatRadius,
                isPressed ? Color.argb(255, 100, 181, 246) : Color.argb(255, 66, 165, 245), // Xanh dương sáng khi nhấn
                Color.argb(255, 21, 101, 192), // Xanh dương đậm
                Shader.TileMode.CLAMP
        ));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // Vẽ vòng ngoài (base)
        canvas.drawCircle(centerX, centerY, baseRadius, basePaint);
        canvas.drawCircle(centerX, centerY, baseRadius, borderPaint); // Vẽ viền

        // Vẽ nút joystick (hat)
        float drawX = isPressed ? touchX : centerX;
        float drawY = isPressed ? touchY : centerY;
        canvas.drawCircle(drawX, drawY, hatRadius, hatPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float dx = event.getX() - centerX;
        float dy = event.getY() - centerY;
        double distance = Math.sqrt(dx * dx + dy * dy);

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (distance < baseRadius) {
                    isPressed = true;
                    touchX = event.getX();
                    touchY = event.getY();
                    updateHatPaint(); // Cập nhật màu khi nhấn
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (isPressed) {
                    float limitRadius = baseRadius - hatRadius; // trừ bán kính nút
                    if (distance < limitRadius) {
                        touchX = event.getX();
                        touchY = event.getY();
                    } else {
                        touchX = (float) (centerX + dx / distance * limitRadius);
                        touchY = (float) (centerY + dy / distance * limitRadius);
                    }
                }
                break;


            case MotionEvent.ACTION_UP:
                isPressed = false;
                touchX = centerX;
                touchY = centerY;
                updateHatPaint(); // Cập nhật màu khi thả
                break;
        }

        invalidate();

        // Gửi phần trăm di chuyển cho listener
        if (listener != null) {
            float xPercent = (touchX - centerX) / baseRadius;
            float yPercent = (touchY - centerY) / baseRadius;
            listener.onJoystickMoved(xPercent, yPercent);
        }

        return true;
    }

    // Cập nhật màu gradient của nút joystick khi nhấn/thả
    private void updateHatPaint() {
        hatPaint.setShader(new RadialGradient(
                touchX, touchY, hatRadius,
                isPressed ? Color.argb(255, 100, 181, 246) : Color.argb(255, 66, 165, 245),
                Color.argb(255, 21, 101, 192),
                Shader.TileMode.CLAMP
        ));
        invalidate();
    }
}