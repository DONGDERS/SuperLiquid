package io.github.liuran001.mmliquidglass;

/**
 * A single critically-parameterised spring, matching Compose's
 * {@code spring(dampingRatio, stiffness, visibilityThreshold)}.
 *
 * <p>KernelSU's bar is animated entirely by springs rather than duration-based
 * interpolators — five of them, each with its own damping and stiffness — so
 * reproducing the feel means reproducing the physics, not approximating it with
 * an {@code OvershootInterpolator}.
 *
 * <p>Integrated semi-implicitly, which stays stable at the stiffnesses used here
 * (up to 1000) as long as steps are clamped to a sane frame time.
 */
final class Spring {

    private final float mStiffness;
    private final float mDampingRatio;
    private final float mThreshold;

    private float mValue;
    private float mVelocity;
    private float mTarget;
    private boolean mRunning;

    Spring(float dampingRatio, float stiffness, float threshold, float initial) {
        mDampingRatio = dampingRatio;
        mStiffness = stiffness;
        mThreshold = threshold;
        mValue = initial;
        mTarget = initial;
    }

    float value() {
        return mValue;
    }

    float target() {
        return mTarget;
    }

    float velocity() {
        return mVelocity;
    }

    boolean isRunning() {
        return mRunning;
    }

    void animateTo(float target) {
        if (mTarget != target) {
            mTarget = target;
            mRunning = true;
        }
    }

    void snapTo(float value) {
        mValue = value;
        mTarget = value;
        mVelocity = 0f;
        mRunning = false;
    }

    /** Advances by {@code dt} seconds; returns true while still in motion. */
    boolean update(float dt) {
        if (!mRunning) {
            return false;
        }
        // Sub-step so a dropped frame cannot blow the integrator up.
        float remaining = Math.min(dt, 0.064f);
        float damping = 2f * mDampingRatio * (float) Math.sqrt(mStiffness);
        while (remaining > 0f) {
            float step = Math.min(remaining, 1f / 240f);
            remaining -= step;
            float accel = -mStiffness * (mValue - mTarget) - damping * mVelocity;
            mVelocity += accel * step;
            mValue += mVelocity * step;
        }
        if (Math.abs(mValue - mTarget) < mThreshold
                && Math.abs(mVelocity) < mThreshold * 10f) {
            mValue = mTarget;
            mVelocity = 0f;
            mRunning = false;
        }
        return mRunning;
    }
}
