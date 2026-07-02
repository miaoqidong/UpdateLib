package com.mqd.updatelib.data

import androidx.datastore.core.DataStore
import com.mqd.updatelib.core.DataStoreFiles
import com.mqd.updatelib.core.UpdateLibContext
import com.mqd.updatelib.core.UpdateState

/**
 * DataStore 持有器（仅包含 update-lib 需要的 DataStore）
 *
 * @author quzhuligpt@gmail.com
 * @since 2026/6/29
 */
object DataStoreHolder {

    val updateState: DataStore<UpdateState> = run {
        val fileName = DataStoreFiles.UPDATE_STATE
        val defValue = UpdateState()
        UpdateLibContext.getContext().dataStore(fileName, defValue)
    }
}
