package xiaofeixia.gesture

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mqd.updatelib.UpdateManager
import com.mqd.updatelib.compose.ui.UpdateHost
import com.mqd.updatelib.compose.ui.UpdateViewModel

class MainActivity : ComponentActivity() {

    private val updateViewModel: UpdateViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            UpdateHost(vm = updateViewModel)
            MainScreen(updateViewModel)
        }

        updateViewModel.onEntry()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        updateViewModel.onEntry()
    }

    override fun onResume() {
        super.onResume()
        updateViewModel.onForeground()
    }
}

@Composable
private fun MainScreen(updateVm: UpdateViewModel) {
    val context = LocalContext.current
    val currentVersion = UpdateManager.getCurrentVersion()
    val isChecking by updateVm.checkingFlow.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(id = xiaofeixia.gesture.R.string.app_name),
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "v$currentVersion",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = { updateVm.checkManually() },
            enabled = !isChecking
        ) {
            if (isChecking) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .height(18.dp)
                            .width(18.dp),
                        strokeWidth = 2.dp
                    )
                    Text(text = stringResource(id = com.mqd.updatelib.R.string.updatelib_update_checking))
                }
            } else {
                Text(text = stringResource(id = com.mqd.updatelib.R.string.updatelib_check_update))
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = {
            context.startActivity(Intent(context, LibKotlin_ViewActivity::class.java))
        }) {
            Text(text = "传统 View 示例 (update-lib库-Kotlin写法)")
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = {
            context.startActivity(Intent(context, LibJava_ViewActivity::class.java))
        }) {
            Text(text = "传统 View 示例 (update-lib库-Java写法)")
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = {
            context.startActivity(Intent(context, Java_ViewActivity::class.java))
        }) {
            Text(text = "传统 View 示例 (update-java库)")
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = {
            context.startActivity(Intent(context, Simple_ViewActivity::class.java))
        }) {
            Text(text = "传统 View 示例 (update-simple库)")
        }
    }
}
