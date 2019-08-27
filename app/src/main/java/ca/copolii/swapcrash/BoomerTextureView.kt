package ca.copolii.swapcrash

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES20
import android.opengl.GLUtils
import android.util.AttributeSet
import android.util.Log
import android.view.TextureView
import java.util.concurrent.atomic.AtomicInteger
import javax.microedition.khronos.egl.*
import javax.microedition.khronos.opengles.GL11
import kotlin.random.Random

private val INSTANCEID = AtomicInteger(0)

private val nextId: Int get() = INSTANCEID.incrementAndGet()

class BoomerTextureView : TextureView {
    private val renderThread: RenderThread

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(
        context,
        attrs,
        defStyleAttr,
        defStyleRes
    )

    init {
        val id = nextId
        val colors = context.resources.getIntArray(R.array.colors)
        renderThread = RenderThread(id, true, colors[id % colors.size])
        surfaceTextureListener = renderThread
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        renderThread.start()
    }

    override fun onDetachedFromWindow() {
        renderThread.halt()
        super.onDetachedFromWindow()
    }

    private inner class RenderThread(
        private val instanceId: Int,
        private val releaseSurfaceTextureInCallback: Boolean,
        color: Int
    ) :
        Thread("WaveformTextureViewRenderer:$instanceId"),
        TextureView.SurfaceTextureListener {
        private val LOCK = Object()

        private val TAG = "WFTVRenderer[$instanceId]"

        private var surfaceTexture: SurfaceTexture? = null
        private var mEgl: EGL10? = null
        private var mEglDisplay: EGLDisplay? = null
        private var mEglConfig: EGLConfig? = null
        private var mEglContext: EGLContext? = null
        private var mEglSurface: EGLSurface? = null
        private var mGl: GL11? = null

        @Volatile
        private var done: Boolean = false

        val BLOCK_WIDTH = 80
        val BLOCK_SPEED = 2
        var xpos: Int = 0
        var xdir: Int = 0
        var width: Int = 0
        var height = 0

        private val A: Float
        private val R: Float
        private val G: Float
        private val B: Float

        init {
            A = color.floater(24)
            R = color.floater(16)
            G = color.floater(8)
            B = color.floater(0)
        }

        fun halt() {
            Log.d(TAG, "Halting renderer")
            synchronized(LOCK) {
                done = true
                LOCK.notify()
            }
        }

        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, w: Int, h: Int) {
            Log.d(TAG, "SurfaceTexture available (${width}x${height})")
            synchronized(LOCK) {
                surfaceTexture = surface
                width = w
                height = h
                LOCK.notify()
            }
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, w: Int, h: Int) {
            Log.d(TAG, "SurfaceTexture resized: (${width}x${height})")
            width = w
            height = h
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            Log.d(TAG, "SurfaceTexture destroyed.")
            halt()
            return releaseSurfaceTextureInCallback
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
            //            Log.d(TAG, "%d: SurfaceTexture updated.", instanceId);
            // Do not care
        }

        override fun run() {
            Log.d(TAG, "Starting renderer.")

            run loop@{
                while (true) {
                    synchronized(LOCK) {
                        while (!done && surfaceTexture == null) {
                            try {
                                LOCK.wait()
                            } catch (ie: InterruptedException) {
                                Log.d(TAG, "Interrupted while waiting for SurfaceTexture")
                            }

                        }

                        Log.d(TAG, "Got SurfaceTexture: $surfaceTexture")
                        if (done) return@loop
                    }

                    initGL()

                    renderLoop()

                    finishGL()
                }
            }

            Log.d(TAG, "Render thread exiting")
        }

        private fun renderLoop() {
            Log.d(TAG, "Entering render loop")

            xpos = Random.nextInt(BLOCK_WIDTH)
            xdir = if (Random.nextBoolean()) BLOCK_SPEED else -BLOCK_SPEED
            Log.d(TAG, "Animating " + width + "x" + height + " EGL surface")

            run loop@{
                while (!done) {
                    synchronized(LOCK) {
                        if (surfaceTexture == null)
                            return@loop

                        checkCurrent()

                        update()
                        drawFrame()

                        if (!mEgl!!.eglSwapBuffers(mEglDisplay, mEglSurface)) {
                            Log.e(TAG, "Cannot swap buffers!")
                        }
                        checkEglError()
                    }

                    try {
                        sleep(DRAW_RATE)
                    } catch (e: InterruptedException) {
                        Log.d(TAG, "Interrupted.")
                    }

                }
            }

            Log.d(TAG, "Exiting render loop")
        }

        private fun update() {
            // Advance state

            xpos += xdir
            if (xpos <= -BLOCK_WIDTH / 2 || xpos >= width - BLOCK_WIDTH / 2) {
                xdir = -xdir
            }
        }

        private fun drawFrame() {
            // Still alive, render a frame.
            GLES20.glClearColor(R, G, B, A)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

            GLES20.glEnable(GLES20.GL_SCISSOR_TEST)
            GLES20.glScissor(xpos, height / 4, BLOCK_WIDTH, height / 2)
            GLES20.glClearColor(1.0f, 0.0f, 0.0f, 1.0f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
        }

        private fun initGL() {
            val egl: EGL10 = (EGLContext.getEGL() as EGL10)
            val display = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY)
            mEgl = egl
            mEglDisplay = display

            if (display === EGL10.EGL_NO_DISPLAY) {
                throw RuntimeException(
                    "${instanceId}: eglGetDisplay failed " + GLUtils.getEGLErrorString(
                        egl.eglGetError()
                    )
                )
            }

            val version = IntArray(2)
            if (!egl.eglInitialize(display, version)) {
                throw RuntimeException(
                    "${instanceId}: eglInitialize failed " + GLUtils.getEGLErrorString(
                        egl.eglGetError()
                    )
                )
            }

            val configChooser = MultiSampleConfigChooser(EGL_CLIENT_VERSION)
            mEglConfig = configChooser.chooseConfig(egl, display)

            mEglContext = egl.eglCreateContext(
                display,
                mEglConfig,
                EGL10.EGL_NO_CONTEXT,
                intArrayOf(EGL_CONTEXT_CLIENT_VERSION, EGL_CLIENT_VERSION, EGL10.EGL_NONE)
            )
            checkEglError()

            mEglSurface = egl.eglCreateWindowSurface(display, mEglConfig, surfaceTexture, null)
            checkEglError()

            if (mEglSurface == null || mEglSurface === EGL10.EGL_NO_SURFACE) {
                val error = egl.eglGetError()
                if (error == EGL10.EGL_BAD_NATIVE_WINDOW) {
                    Log.e(TAG, "eglCreateWindowSurface returned EGL10.EGL_BAD_NATIVE_WINDOW")
                    return
                }
                throw RuntimeException(
                    "${instanceId}: eglCreateWindowSurface failed " + GLUtils.getEGLErrorString(
                        error
                    )
                )
            }

            if (!egl.eglMakeCurrent(display, mEglSurface, mEglSurface, mEglContext)) {
                throw RuntimeException(
                    "${instanceId}: eglMakeCurrent failed " + GLUtils.getEGLErrorString(
                        egl.eglGetError()
                    )
                )
            }
            checkEglError()

            mGl = mEglContext!!.gl as GL11
            checkEglError()
        }

        private fun checkCurrent() {
            if (mEglContext != mEgl!!.eglGetCurrentContext() || mEglSurface != mEgl!!.eglGetCurrentSurface(
                    EGL10.EGL_DRAW
                )
            ) {
                checkEglError()
                if (!mEgl!!.eglMakeCurrent(mEglDisplay, mEglSurface, mEglSurface, mEglContext)) {
                    throw RuntimeException(
                        "${instanceId}: eglMakeCurrent failed " + GLUtils.getEGLErrorString(
                            mEgl!!.eglGetError()
                        )
                    )
                }
                checkEglError()
            }
        }

        private fun finishGL() {
            mEgl!!.eglDestroyContext(mEglDisplay, mEglContext)
            mEgl!!.eglDestroySurface(mEglDisplay, mEglSurface)

            synchronized(LOCK) {
                if (surfaceTexture != null) {
                    Log.d(TAG, "Releasing SurfaceTexture")
                    if (releaseSurfaceTextureInCallback)
                        surfaceTexture!!.release()

                    surfaceTexture = null
                } else
                    Log.d(TAG, "No SurfaceTexture to release")
            }
        }

        private fun checkEglError() {
            val error = mEgl!!.eglGetError()
            if (error != EGL10.EGL_SUCCESS) {
                Log.e(TAG, "egl error = 0x$error")
            }
        }
    }
}

private val EGL_CLIENT_VERSION = 2
private val EGL_CONTEXT_CLIENT_VERSION = 0x3098
private val DRAW_RATE = 48L

private fun Int.floater(bits: Int): Float = (shr(bits).toFloat() / 0xff.toFloat()).coerceIn(0f, 1f)