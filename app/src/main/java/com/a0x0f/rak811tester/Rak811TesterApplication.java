package com.a0x0f.rak811tester;

import android.app.Application;

public class Rak811TesterApplication extends Application {

    private static Rak811TesterApplication instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    public static Rak811TesterApplication getInstance() {
        return instance;
    }
}
