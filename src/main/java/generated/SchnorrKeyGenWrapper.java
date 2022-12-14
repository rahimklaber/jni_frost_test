// Automatically generated by flapigen
package generated;


public final class SchnorrKeyGenWrapper {

    public SchnorrKeyGenWrapper(int threshold, int n, int index, String context) {
        mNativeObj = init(threshold, n, index, context);
    }
    private static native long init(int threshold, int n, int index, String context);

    public static ResultKeygen1 key_gen_1_create_commitments(SchnorrKeyGenWrapper wrapper) {
        long a0 = wrapper.mNativeObj;
        wrapper.mNativeObj = 0;

        long ret = do_key_gen_1_create_commitments(a0);
        ResultKeygen1 convRet = new ResultKeygen1(InternalPointerMarker.RAW_PTR, ret);
        java.lang.ref.Reference.reachabilityFence(wrapper);

        return convRet;
    }
    private static native long do_key_gen_1_create_commitments(long wrapper);

    public static ResultKeygen2 key_gen_2_generate_shares(SchnorrKeyGenWrapper wrapper, ParamsKeygen2 param_wrapper) {
        long a0 = wrapper.mNativeObj;
        wrapper.mNativeObj = 0;

        long a1 = param_wrapper.mNativeObj;
        param_wrapper.mNativeObj = 0;

        long ret = do_key_gen_2_generate_shares(a0, a1);
        ResultKeygen2 convRet = new ResultKeygen2(InternalPointerMarker.RAW_PTR, ret);
        java.lang.ref.Reference.reachabilityFence(wrapper);
        java.lang.ref.Reference.reachabilityFence(param_wrapper);

        return convRet;
    }
    private static native long do_key_gen_2_generate_shares(long wrapper, long param_wrapper);

    public static SchnorrKeyWrapper key_gen_3_complete(SchnorrKeyGenWrapper wrapper, ParamsKeygen3 param_wrapper) {
        long a0 = wrapper.mNativeObj;
        wrapper.mNativeObj = 0;

        long a1 = param_wrapper.mNativeObj;
        param_wrapper.mNativeObj = 0;

        long ret = do_key_gen_3_complete(a0, a1);
        SchnorrKeyWrapper convRet = new SchnorrKeyWrapper(InternalPointerMarker.RAW_PTR, ret);
        java.lang.ref.Reference.reachabilityFence(wrapper);
        java.lang.ref.Reference.reachabilityFence(param_wrapper);

        return convRet;
    }
    private static native long do_key_gen_3_complete(long wrapper, long param_wrapper);

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
    /*package*/ SchnorrKeyGenWrapper(InternalPointerMarker marker, long ptr) {
        assert marker == InternalPointerMarker.RAW_PTR;
        this.mNativeObj = ptr;
    }
    /*package*/ long mNativeObj;
}