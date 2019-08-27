package ca.copolii.swapcrash

import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val TAG = "MAIN"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
//        goBoom()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, R.id.action_boom, 0, "Boom")
            .setIcon(R.drawable.ic_boom)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)

        menu.add(0, R.id.action_clear, 1, "Clear")
            .setIcon(R.drawable.ic_clear)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_boom -> goBoom()
            R.id.action_clear -> unBoom()
            else -> return super.onOptionsItemSelected(item)
        }

        return true
    }

    private fun unBoom() {
        val fragment = supportFragmentManager.findFragmentById(R.id.container) ?: return

        supportFragmentManager.beginTransaction().remove(fragment).commit()
    }

    private fun goBoom() {
        Log.d(TAG, "BOOM")
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, BoomerFragment())
            .commit()
    }
}
