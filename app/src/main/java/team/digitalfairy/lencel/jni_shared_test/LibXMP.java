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



}
