package com.signaltalk.app;

import android.car.Car;
import android.car.VehicleGear;
import android.car.VehiclePropertyIds;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * 차량 속도·기어·주차 브레이크를 구독해 "정차 / 주행"을 판단한다.
 * 정차 조건: 기어 P, 또는 주차 브레이크 ON, 또는 속도 0이 2초 이상 유지
 */
public class DrivingStateMonitor {

    public interface Listener {
        /** 정차/주행 상태가 바뀔 때만 호출 */
        void onStoppedChanged(boolean stopped);
        /** 값이 바뀔 때마다 호출 (화면 표시용) */
        void onValues(float kmh, String gear, boolean parkingBrake);
    }

    private static final String TAG = "SignalTalk";
    private static final long STOP_HOLD_MS = 2000;

    private final Context context;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private Car car;
    private CarPropertyManager cpm;

    private float speed = 0f;      // m/s
    private int gear = -1;
    private boolean parkingBrake = false;
    private long zeroSince = -1;
    private Boolean stopped = null; // 아직 모름

    public DrivingStateMonitor(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void start() {
        car = Car.createCar(context);
        if (car == null) {
            Log.e(TAG, "Car 연결 실패");
            return;
        }
        cpm = (CarPropertyManager) car.getCarManager(Car.PROPERTY_SERVICE);
        register(VehiclePropertyIds.PERF_VEHICLE_SPEED);
        register(VehiclePropertyIds.GEAR_SELECTION);
        register(VehiclePropertyIds.PARKING_BRAKE_ON);
        handler.post(tick);
    }

    public void stop() {
        handler.removeCallbacks(tick);
        if (cpm != null) cpm.unregisterCallback(callback);
        if (car != null) car.disconnect();
    }

    public boolean isStopped() {
        return stopped == null || stopped;
    }

    private void register(int propId) {
        try {
            boolean ok = cpm.registerCallback(callback, propId, CarPropertyManager.SENSOR_RATE_ONCHANGE);
            Log.d(TAG, "register " + VehiclePropertyIds.toString(propId) + " = " + ok);
        } catch (Exception e) {
            Log.e(TAG, "register 실패: " + VehiclePropertyIds.toString(propId), e);
        }
    }

    private final CarPropertyManager.CarPropertyEventCallback callback =
            new CarPropertyManager.CarPropertyEventCallback() {
        @Override
        public void onChangeEvent(CarPropertyValue value) {
            int id = value.getPropertyId();
            Object v = value.getValue();
            if (id == VehiclePropertyIds.PERF_VEHICLE_SPEED) {
                speed = (Float) v;
                if (Math.abs(speed) < 0.1f) {
                    if (zeroSince < 0) zeroSince = System.currentTimeMillis();
                } else {
                    zeroSince = -1;
                }
            } else if (id == VehiclePropertyIds.GEAR_SELECTION) {
                gear = (Integer) v;
            } else if (id == VehiclePropertyIds.PARKING_BRAKE_ON) {
                parkingBrake = (Boolean) v;
            }
            evaluate();
        }

        @Override
        public void onErrorEvent(int propId, int zone) {
            Log.e(TAG, "error " + VehiclePropertyIds.toString(propId));
        }
    };

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            evaluate();
            handler.postDelayed(this, 500);
        }
    };

    private void evaluate() {
        if (gear < 0) return; // 기어 값을 아직 못 받았으면 판단 보류 (시작 직후 잘못된 "출발" 방지)
        boolean zeroLongEnough = zeroSince > 0
                && System.currentTimeMillis() - zeroSince >= STOP_HOLD_MS;
        boolean now = gear == VehicleGear.GEAR_PARK || parkingBrake || zeroLongEnough;
        if (stopped == null || now != stopped) {
            stopped = now;
            Log.d(TAG, now ? "정차 → 요약 화면 표시" : "출발 → 요약 화면 숨김");
            listener.onStoppedChanged(now);
        }
        listener.onValues(speed * 3.6f, gear < 0 ? "?" : VehicleGear.toString(gear), parkingBrake);
    }
}
