package ca.copolii.swapcrash

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import androidx.fragment.app.Fragment

class BoomerFragment : Fragment() {
    private val TAG = "BOOMER"
    private lateinit var textureViews: List<TextureView>

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
        inflater.inflate(R.layout.fragment_boomer, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        textureViews = gatherThemTextureViews(view)?.toList() ?: emptyList()
        Log.d(TAG, "Found ${textureViews.size} TextureViews")
    }

    private fun gatherThemTextureViews(parent: View): List<TextureView>? {
        val list = mutableListOf<TextureView>()

        when (parent) {
            is TextureView -> list.add(parent)
            is ViewGroup -> parent.children.forEach { view ->
                gatherThemTextureViews(view)?.let { list.addAll(it) }
            }
        }

        return list
    }
}

inline fun ViewGroup.children() = object : Iterable<View> {
    override fun iterator() = object : Iterator<View> {
        var index = 0

        override fun hasNext(): Boolean = index < childCount

        override fun next(): View = getChildAt(index++)
    }
}
