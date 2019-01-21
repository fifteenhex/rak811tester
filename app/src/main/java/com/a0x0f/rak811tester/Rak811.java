package com.a0x0f.rak811tester;


import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;
import com.google.android.gms.common.util.Hex;
import com.google.gson.Gson;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;


public class Rak811 {

    private static final String TAG = Rak811.class.getSimpleName();
    private static final String CMD_VERSION = "version";
    private static final String CMD_SEND = "send";
    private static final String CMD_SIGNAL = "signal";

    private static final Pattern SIGNAL_PATTERN = Pattern.compile("OK(-{0,1}[0-9]{1,3}),([0-9]{1,3})");

    private static final int EVENTCODE_STATUS_TX_CONFIRMED = 1;
    private static final int EVENTCODE_STATUS_JOINED_SUCCESS = 3;

    private final UsbSerialDevice port;

    private Rak811(UsbSerialDevice port) {
        this.port = port;
    }

    private static String readLine(UsbSerialDevice port) {
        return readLine(port, 0);
    }

    private static String readLine(UsbSerialDevice port, long timeout) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(64);
        byte[] temp = new byte[64];

        long startedAt = System.currentTimeMillis();

        while (true) {
            if (timeout > 0) {
                long elapsed = System.currentTimeMillis() - startedAt;
                if (elapsed > timeout)
                    break;
            }

            int read = port.syncRead(temp, 1000);

            if (read <= 0)
                continue;

            byteBuffer.put(temp, 0, read);
            if (temp[read - 1] == '\n')
                break;
        }

        if (byteBuffer.position() == 0)
            return null;

        byteBuffer.flip();
        byte[] bytes = new byte[byteBuffer.limit()];
        byteBuffer.get(bytes);
        try {
            String line = new String(bytes, "ascii").replace("\r\n", "");
            return line;
        } catch (UnsupportedEncodingException uee) {
            throw new RuntimeException();
        }
    }

    private static String doCommand(UsbSerialDevice port,
                                    String command,
                                    String... parameters) {
        synchronized (port) {
            try {
                StringBuffer sb = new StringBuffer();
                sb.append("at+");
                sb.append(command);
                if (parameters != null && parameters.length > 0) {
                    sb.append("=");
                    for (String p : parameters) {
                        sb.append(p);
                        sb.append(",");
                    }
                    sb.deleteCharAt(sb.length() - 1);
                }
                sb.append("\r\n");

                String fullCommand = sb.toString();
                Logger.d(TAG, "command: " + fullCommand.replace("\r\n", ""));
                byte[] commandBytes = fullCommand.getBytes("ascii");
                port.syncWrite(commandBytes, 1000);

                String result = readLine(port);
                Logger.d(TAG, "result: " + result);


                return result;
            } catch (UnsupportedEncodingException uee) {
                throw new RuntimeException();
            } catch (IOException ioe) {
                return null;
            }
        }
    }

    private static Result waitForResult(UsbSerialDevice port) {
        String line = readLine(port);
        Logger.d(TAG, "result: " + line);
        return Result.from(line);
    }

    private static Downlink waitForDownlink(UsbSerialDevice port) {
        String line = readLine(port, 10000);
        if (line != null) {
            Logger.d(TAG, "downlink: " + line);
            return Downlink.from(line);
        } else
            return null;
    }

    @WorkerThread
    public static Rak811 from(UsbSerialDevice port) {
        if (port.syncOpen()) {
            port.setBaudRate(115200);
            port.setDataBits(UsbSerialInterface.DATA_BITS_8);
            port.setStopBits(UsbSerialInterface.STOP_BITS_1);
            port.setParity(UsbSerialInterface.PARITY_NONE);
            port.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);

            reset(port);

            if (doCommand(port, CMD_VERSION).startsWith("OK"))
                return new Rak811(port);
        }
        return null;
    }

    static private void reset(UsbSerialDevice port) {
        Logger.d(TAG, "doing reset..");
        try {
            port.setDTR(true);
            Thread.sleep(200);
            port.setDTR(false);
            Thread.sleep(200);
        } catch (InterruptedException ie) {

        }

        String line;
        Logger.d(TAG, "draining...");
        while ((line = readLine(port, 5000)) != null)
            Logger.d(TAG, "drained line: " + line);
        Logger.d(TAG, "drained.");
    }

    public void reset() {
        reset(port);
    }

    @WorkerThread
    public boolean join() {
        doCommand(this.port, "join", "otaa");
        Result result = waitForResult(this.port);
        return result.eventCode == EVENTCODE_STATUS_JOINED_SUCCESS;
    }

    @WorkerThread
    public boolean send(boolean confirmed, int port, byte[] data) {
        if (port < 0 || port > 0xff)
            throw new IllegalArgumentException("Port must be between 0 and 255");

        String dataString = Hex.bytesToStringUppercase(data);
        doCommand(this.port, CMD_SEND, confirmed ? "1" : "0", Integer.toString(port), dataString);
        Result result = waitForResult(this.port);

        Downlink downlink = waitForDownlink(this.port);

        return result.eventCode == EVENTCODE_STATUS_TX_CONFIRMED;
    }

    @WorkerThread
    public Signal signal() {
        String result = doCommand(this.port, CMD_SIGNAL);
        return Signal.from(result);
    }

    public static class Result {

        protected static Pattern recvPattern =
                Pattern.compile("at\\+recv=([0-9]{1,3}),([0-9]{1,3}),([0-9]{1,3})");

        public final int eventCode;
        public final int port;
        public int len;

        private Result(int eventCode, int port, int len) {
            this.eventCode = eventCode;
            this.port = port;
            this.len = len;
        }

        public static Result from(String buffer) {
            if (buffer == null)
                throw new IllegalArgumentException();

            Matcher matcher = recvPattern.matcher(buffer);
            if (matcher.find()) {
                int eventCode = Integer.parseInt(matcher.group(1));
                int port = Integer.parseInt(matcher.group(2));
                int len = Integer.parseInt(matcher.group(3));
                Result result = new Result(eventCode, port, len);
                return result;
            }

            return null;
        }

        @NonNull
        @Override
        public String toString() {
            return new Gson().toJson(this);
        }
    }

    public static class Downlink extends Result {

        private Downlink(int eventCode, int port, int len) {
            super(eventCode, port, len);
        }

        public static Downlink from(String buffer) {
            if (buffer == null)
                throw new IllegalArgumentException();

            Matcher matcher = recvPattern.matcher(buffer);
            if (matcher.find()) {
                int eventCode = Integer.parseInt(matcher.group(1));
                int port = Integer.parseInt(matcher.group(2));
                int len = Integer.parseInt(matcher.group(3));
                Downlink downlink = new Downlink(eventCode, port, len);
                return downlink;
            }

            return null;
        }
    }

    public static class Signal {
        public final int rssi;
        public final int snr;

        private Signal(int rssi, int snr) {
            this.rssi = rssi;
            this.snr = snr;
        }

        public static Signal from(String line) {
            Matcher matcher = SIGNAL_PATTERN.matcher(line);
            if (matcher.find()) {
                return new Signal(Integer.parseInt(matcher.group(1)),
                        Integer.parseInt(matcher.group(2)));
            }
            return null;
        }
    }

}
