package team.digitalfairy.lencel.jni_shared_test;

public class LibXMP {
    public static native void helloworld();

    public static native String getXMPVersion();

    // Android - Control OpenSLES
    public static native void startOpenSLES(int nsr, int fpb);

    // libxmp function
    public static native boolean loadFile(String filename);
    public static native void togglePause();

    public static native String getFrameInfo();

    // information
    public static native int getChannels();
    public static native String getChannelInfo(int ch);

    // information
    public static native long getRunningTime();
    public static native long getTotalTime();


    public static native String getRowEvt(int row, int channel);
    public static native int getCurrentRow();
    public static native int getCurrentPattern();
    public static native int getTotalRows();
    public static native String[] getRowString(int pattern);
}
