package com.a0x0f.rak811tester;


import android.util.Log;

import io.reactivex.subjects.PublishSubject;

public class Logger {

    public static class LogLine {
        public final String tag;
        public final String message;

        private LogLine(String tag, String message) {
            this.tag = tag;
            this.message = message;
        }
    }

    private final static Logger instance = new Logger();

    public final PublishSubject<LogLine> logLines = PublishSubject.create();

    private Logger() {

    }

    public static void d(String tag, String message) {
        Log.d(tag, message);
        instance.logLines.onNext(new LogLine(tag, message));
    }

    public static PublishSubject<LogLine> getLogLines() {
        return instance.logLines;
    }

}
