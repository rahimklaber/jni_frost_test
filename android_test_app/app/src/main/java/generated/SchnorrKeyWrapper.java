// Automatically generated by flapigen
package generated;


public final class SchnorrKeyWrapper {

    public SchnorrKeyWrapper() {
        mNativeObj = init();
    }
    private static native long init();

    public final byte [] get_bitcoin_encoded_key() {
        byte [] ret = do_get_bitcoin_encoded_key(mNativeObj);

        return ret;
    }
    private static native byte [] do_get_bitcoin_encoded_key(long self);

    public synchronized void delete() {
        if (mNativeObj != 0) {
            do_delete(mNativeObj);
            mNativeObj = 0;
       }
    }
    @Override
    protected void finalize() throws Throwable {
        try {
            delete();
        }
        finally {
             super.finalize();
        }
    }
    private static native void do_delete(long me);
    /*package*/ SchnorrKeyWrapper(InternalPointerMarker marker, long ptr) {
        assert marker == InternalPointerMarker.RAW_PTR;
        this.mNativeObj = ptr;
    }
    /*package*/ long mNativeObj;
}