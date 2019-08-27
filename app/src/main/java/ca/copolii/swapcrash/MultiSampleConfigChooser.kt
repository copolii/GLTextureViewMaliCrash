package ca.copolii.swapcrash

import android.opengl.EGL14
import android.opengl.EGLExt
import android.opengl.GLSurfaceView

import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay

internal class MultiSampleConfigChooser(private val GLESVersion: Int) : GLSurfaceView.EGLConfigChooser {

    override fun chooseConfig(egl: EGL10, display: EGLDisplay): EGLConfig {

        val value = IntArray(1)

        // Try to find a normal multisample config first.
        var configSpec = filterConfigSpec(
            EGL10.EGL_RED_SIZE, 8,
            EGL10.EGL_GREEN_SIZE, 8,
            EGL10.EGL_BLUE_SIZE, 8,
            EGL10.EGL_ALPHA_SIZE, 8,
            EGL10.EGL_DEPTH_SIZE, 16,
            EGL10.EGL_STENCIL_SIZE, 0,
            EGL10.EGL_SAMPLE_BUFFERS, 1 /* true */,
            EGL10.EGL_SAMPLES, 4,
            EGL10.EGL_NONE
        )

        require(egl.eglChooseConfig(display, configSpec, null, 0, value)) { "1st eglChooseConfig failed" }

        var numConfigs = value[0]

        if (numConfigs <= 0) {
            // No normal multisampling config was found. Try to create a
            // coverage multisampling configuration, for the nVidia Tegra2.
            // See the EGL_NV_coverage_sample documentation.

            val EGL_COVERAGE_BUFFERS_NV = 0x30E0
            val EGL_COVERAGE_SAMPLES_NV = 0x30E1

            configSpec = filterConfigSpec(
                EGL10.EGL_RED_SIZE, 8,
                EGL10.EGL_GREEN_SIZE, 8,
                EGL10.EGL_BLUE_SIZE, 8,
                EGL10.EGL_ALPHA_SIZE, 8,
                EGL10.EGL_DEPTH_SIZE, 16,
                EGL10.EGL_STENCIL_SIZE, 0,
                EGL_COVERAGE_BUFFERS_NV, 1 /* true */,
                EGL_COVERAGE_SAMPLES_NV, 2, // always 5 in practice on tegra 2
                EGL10.EGL_NONE
            )

            require(egl.eglChooseConfig(display, configSpec, null, 0, value)) { "2nd eglChooseConfig failed" }

            numConfigs = value[0]

            if (numConfigs <= 0) {
                // Give up, try without multisampling.

                configSpec = filterConfigSpec(
                    EGL10.EGL_RED_SIZE, 8,
                    EGL10.EGL_GREEN_SIZE, 8,
                    EGL10.EGL_BLUE_SIZE, 8,
                    EGL10.EGL_ALPHA_SIZE, 8,
                    EGL10.EGL_DEPTH_SIZE, 16,
                    EGL10.EGL_STENCIL_SIZE, 0,
                    EGL10.EGL_NONE
                )

                require(egl.eglChooseConfig(display, configSpec, null, 0, value)) { "3rd eglChooseConfig failed" }

                numConfigs = value[0]

                require(numConfigs > 0) { "No configs match configSpec" }
            }
        }

        val configs = arrayOfNulls<EGLConfig>(numConfigs)

        require(egl.eglChooseConfig(display, configSpec, configs, numConfigs, value)) { "data eglChooseConfig failed" }

        return (if (value[0] == 0) null else configs[0]) ?: throw IllegalArgumentException("No config chosen")
    }

    private fun filterConfigSpec(vararg configSpec: Int): IntArray {
        if (GLESVersion != 2 && GLESVersion != 3) {
            return configSpec
        }

        /* We know none of the subclasses define EGL_RENDERABLE_TYPE.
         * And we know the configSpec is well formed.
         */
        val len = configSpec.size
        val newConfigSpec = IntArray(len + 2)

        System.arraycopy(configSpec, 0, newConfigSpec, 0, len - 1)
        newConfigSpec[len - 1] = EGL10.EGL_RENDERABLE_TYPE

        when (GLESVersion) {
            2 -> newConfigSpec[len] = EGL14.EGL_OPENGL_ES2_BIT  /* EGL_OPENGL_ES2_BIT */
            3 -> newConfigSpec[len] = EGLExt.EGL_OPENGL_ES3_BIT_KHR /* EGL_OPENGL_ES3_BIT_KHR */
        }

        newConfigSpec[len + 1] = EGL10.EGL_NONE
        return newConfigSpec
    }
}
